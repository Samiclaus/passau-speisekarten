package de.passau.speisekarten.mensa;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manual live smoke test for the STWNO HTML endpoint.
 *
 * <p>Checks seven consecutive days starting on the selected date (today by
 * default) for the Passau university cafeteria.</p>
 */
public final class MensaHtmlLiveCheck
{
    private static final String LOCATION_ID = "UNI-P";
    private static final int DAYS_TO_CHECK = 7;

    private MensaHtmlLiveCheck()
    {
    }

    public static void main(String[] args)
    {
        LocalDate startDate = args.length > 0 && !args[0].isBlank()
            ? LocalDate.parse(args[0])
            : LocalDate.now();

        LocalDate endDate = startDate.plusDays(DAYS_TO_CHECK - 1);
        System.out.printf(
            "STWNO HTML-Wochencheck — Standort %s, Zeitraum %s bis %s (%d Tage)%n",
            LOCATION_ID,
            startDate,
            endDate,
            DAYS_TO_CHECK
        );

        MensaHtmlPostClient client = new MensaHtmlPostClient(
            MensaHtmlPostClient.DEFAULT_ENDPOINT,
            LOCATION_ID
        );
        MensaHtmlResponseParser parser = new MensaHtmlResponseParser();
        List<DayResult> results = new ArrayList<>();

        for (int dayOffset = 0; dayOffset < DAYS_TO_CHECK; dayOffset++)
        {
            LocalDate date = startDate.plusDays(dayOffset);
            System.out.printf("%n%s — %s%n", date.getDayOfWeek(), date);
            System.out.println("Rufe Speiseplan ab ...");

            try
            {
                Document response = client.fetch(date);
                String html = response.html();
                List<MensaHtmlDish> dishes = parser.parse(html);

                System.out.printf(
                    "HTTP-Antwort verarbeitet: %d Zeichen HTML, %d erkannte Gerichte%n",
                    html.length(),
                    dishes.size()
                );

                if (dishes.isEmpty())
                {
                    if (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                        || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY)
                    {
                        System.out.println("Kein Speiseplan am Wochenende erwartet.");
                    }
                    else
                    {
                        System.err.println("WARNUNG: An einem Werktag wurden keine Gerichte erkannt.");
                        System.err.println("Antworttext (max. 1200 Zeichen) zur Diagnose:");
                        String text = response.text().replaceAll("\\s+", " ").trim();
                        System.err.println(text.substring(0, Math.min(text.length(), 1200)));
                        System.err.println("Prüfe, ob für diesen Tag ein Speiseplan veröffentlicht ist.");
                    }
                }
                else
                {
                    printDishes(dishes);
                }

                long withoutCategory = dishes.stream()
                    .filter(dish -> dish.category() == null || dish.category().isBlank())
                    .count();
                long withoutAllPrices = dishes.stream()
                    .filter(dish -> dish.studentPrice() == null
                        && dish.employeePrice() == null
                        && dish.guestPrice() == null)
                    .count();
                long withoutStudentPrice = dishes.stream()
                    .filter(dish -> dish.studentPrice() == null)
                    .count();
                long withoutEmployeePrice = dishes.stream()
                    .filter(dish -> dish.employeePrice() == null)
                    .count();
                long withoutGuestPrice = dishes.stream()
                    .filter(dish -> dish.guestPrice() == null)
                    .count();

                System.out.printf(
                    "Tagesprüfung: %d Gerichte, %d ohne Kategorie, " +
                    "Preise fehlen (Studierende/Beschäftigte/Gäste): %d/%d/%d%n",
                    dishes.size(),
                    withoutCategory,
                    withoutStudentPrice,
                    withoutEmployeePrice,
                    withoutGuestPrice
                );
                results.add(new DayResult(
                    date,
                    dishes.size(),
                    withoutCategory,
                    withoutAllPrices,
                    withoutStudentPrice,
                    withoutEmployeePrice,
                    withoutGuestPrice,
                    null
                ));
            }
            catch (IOException | RuntimeException exception)
            {
                String error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                System.err.printf("FEHLER für %s: %s%n", date, error);
                results.add(new DayResult(date, 0, 0, 0, 0, 0, 0, error));
            }
        }

        printWeeklySummary(results);
    }

    private static void printDishes(List<MensaHtmlDish> dishes)
    {
        System.out.printf("%-18s | %-64s | %-35s | %10s | %10s | %10s%n",
            "Kategorie", "Gericht", "Kennzeichen", "Studierende", "Beschäftigte", "Gäste");
        System.out.println("-".repeat(171));

        for (MensaHtmlDish dish : dishes)
        {
            String category = truncate(
                dish.category() == null ? "(ohne Kategorie)" : dish.category(),
                18
            );
            String name = truncate(dish.name(), 64);
            String markers = formatMarkers(dish.markers());
            System.out.printf("%-18s | %-64s | %-35s | %10s | %10s | %10s%n",
                category,
                name,
                truncate(markers, 35),
                price(dish.studentPrice()),
                price(dish.employeePrice()),
                price(dish.guestPrice()));
        }
    }

    private static void printWeeklySummary(List<DayResult> results)
    {
        System.out.println();
        System.out.println("Wochenübersicht");
        System.out.printf("%-12s | %8s | %14s | %10s | %10s | %10s | %s%n",
            "Datum", "Gerichte", "ohne Kategorie", "ohne Stud.", "ohne Besch.",
            "ohne Gäste", "Status");
        System.out.println("-".repeat(110));

        for (DayResult result : results)
        {
            String status;
            if (result.error() != null)
            {
                status = "FEHLER: " + result.error();
            }
            else if (result.dishCount() == 0
                && (result.date().getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || result.date().getDayOfWeek() == java.time.DayOfWeek.SUNDAY))
            {
                status = "Wochenende (erwartet)";
            }
            else if (result.dishCount() == 0)
            {
                status = "Keine Gerichte erkannt";
            }
            else if (result.withoutCategory() > 0
                || result.withoutStudentPrice() > 0
                || result.withoutEmployeePrice() > 0
                || result.withoutGuestPrice() > 0)
            {
                status = "Prüfen: Angaben fehlen";
            }
            else
            {
                status = "OK";
            }
            System.out.printf("%-12s | %8d | %14d | %10d | %10d | %10d | %s%n",
                result.date(),
                result.dishCount(),
                result.withoutCategory(),
                result.withoutStudentPrice(),
                result.withoutEmployeePrice(),
                result.withoutGuestPrice(),
                status);
        }

        int failedDays = (int) results.stream().filter(result -> result.error() != null).count();
        int daysWithDishes = (int) results.stream().filter(result -> result.dishCount() > 0).count();
        int totalDishes = results.stream().mapToInt(DayResult::dishCount).sum();
        long withoutAllPrices = results.stream().mapToLong(DayResult::withoutAllPrices).sum();

        System.out.printf(
            "%nGesamt: %d Tage geprüft, %d Abruffehler, %d Tage mit Gerichten, " +
            "%d Gerichte insgesamt, %d Gerichte ohne sämtliche Preise.%n",
            results.size(),
            failedDays,
            daysWithDishes,
            totalDishes,
            withoutAllPrices
        );
    }

    private static String formatMarkers(Set<MensaMarker> markers)
    {
        if (markers == null || markers.isEmpty())
        {
            return "—";
        }
        return markers.stream()
            .sorted(Comparator.comparing(MensaMarker::name))
            .map(Enum::name)
            .collect(Collectors.joining(", "));
    }

    private static String price(BigDecimal value)
    {
        return value == null ? "—" : value.setScale(2).toPlainString() + " €";
    }

    private static String truncate(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        if (value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private record DayResult(
        LocalDate date,
        int dishCount,
        long withoutCategory,
        long withoutAllPrices,
        long withoutStudentPrice,
        long withoutEmployeePrice,
        long withoutGuestPrice,
        String error
    )
    {
    }
}
