package de.passau.speisekarten.mensa;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manual live smoke test for the STWNO HTML endpoint.
 *
 * <p>Run from {@code importer/} with:
 * {@code mvn -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java
 * -Dexec.mainClass=de.passau.speisekarten.mensa.MensaHtmlLiveCheck
 * -Dexec.args="2026-10-26 UNI-P"}.</p>
 */
public final class MensaHtmlLiveCheck
{
    private MensaHtmlLiveCheck()
    {
    }

    public static void main(String[] args) throws IOException
    {
        LocalDate date = args.length > 0 && !args[0].isBlank()
            ? LocalDate.parse(args[0])
            : LocalDate.now();
        String locationId = args.length > 1 && !args[1].isBlank()
            ? args[1]
            : "UNI-P";

        System.out.printf("STWNO HTML-Livecheck — Standort %s, Datum %s%n", locationId, date);
        System.out.println("Rufe Speiseplan ab ...");

        Document response = new MensaHtmlPostClient(
            MensaHtmlPostClient.DEFAULT_ENDPOINT,
            locationId
        ).fetch(date);

        String html = response.html();
        List<MensaHtmlDish> dishes = new MensaHtmlResponseParser().parse(html);

        System.out.printf("HTTP-Antwort verarbeitet: %d Zeichen HTML, %d erkannte Gerichte%n%n",
            html.length(), dishes.size());

        if (dishes.isEmpty())
        {
            System.err.println("WARNUNG: Keine Gerichte erkannt.");
            System.err.println("Antworttext (max. 1200 Zeichen) zur Diagnose:");
            String text = response.text().replaceAll("\\s+", " ").trim();
            System.err.println(text.substring(0, Math.min(text.length(), 1200)));
            System.err.println("Prüfe, ob der Standort/das Datum einen Speiseplan hat oder sich das Quell-HTML geändert hat.");
            return;
        }

        System.out.printf("%-18s | %-64s | %-35s | %8s | %8s | %8s%n",
            "Kategorie", "Gericht", "Kennzeichen", "Stud.", "Besch.", "Gast");
        System.out.println("-".repeat(155));

        for (MensaHtmlDish dish : dishes)
        {
            String category = truncate(dish.category() == null ? "(ohne Kategorie)" : dish.category(), 18);
            String name = truncate(dish.name(), 64);
            String markers = formatMarkers(dish.markers());
            System.out.printf("%-18s | %-64s | %-35s | %8s | %8s | %8s%n",
                category,
                name,
                truncate(markers, 35),
                price(dish.studentPrice()),
                price(dish.employeePrice()),
                price(dish.guestPrice()));
        }

        long withoutCategory = dishes.stream().filter(dish -> dish.category() == null || dish.category().isBlank()).count();
        long withoutPrices = dishes.stream().filter(dish ->
            dish.studentPrice() == null && dish.employeePrice() == null && dish.guestPrice() == null).count();

        System.out.printf("%nKurzprüfung: %d Gerichte, %d ohne Kategorie, %d ohne sämtliche Preise.%n",
            dishes.size(), withoutCategory, withoutPrices);
        if (withoutCategory > 0 || withoutPrices > 0)
        {
            System.out.println("Bitte die betreffenden Quellzeilen prüfen; fehlende Preise können je nach Gericht legitim sein.");
        }
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
}
