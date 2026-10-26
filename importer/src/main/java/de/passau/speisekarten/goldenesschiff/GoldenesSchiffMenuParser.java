package de.passau.speisekarten.goldenesschiff;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts dated main-dish pairs from the Goldenes Schiff weekly PDF. */
public final class GoldenesSchiffMenuParser {
    private static final Pattern DATE = Pattern.compile(
        "(?i)^(?:Montag|Dienstag|Mittwoch|Donnerstag|Freitag|Samstag|Sonntag),?\\s+(\\d{1,2})\\.?\\s+([\\p{L}]+)(?:\\s+(20\\d{2}))?.*$");
    private static final Locale DE = Locale.GERMAN;

    public List<DayMenu> parse(InputStream pdf, String fileName, LocalDate referenceDate) throws IOException {
        try (var document = Loader.loadPDF(pdf.readAllBytes())) {
            return parse(new PDFTextStripper().getText(document), referenceDate);
        }
    }

    List<DayMenu> parse(String text, LocalDate referenceDate) {
        List<String> lines = text.replace('\r', '\n').lines().map(String::trim).filter(s -> !s.isBlank()).toList();
        List<DayMenu> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = DATE.matcher(lines.get(i));
            if (!matcher.matches()) continue;
            LocalDate date = parseDate(matcher.group(1), matcher.group(2), matcher.group(3), referenceDate);
            List<String> dishes = new ArrayList<>();
            for (int j = i + 1; j < lines.size() && dishes.size() < 2; j++) {
                if (DATE.matcher(lines.get(j)).matches()) break;
                String line = lines.get(j).replaceAll("\\s+[0-9]+(?:,[0-9]+)*\\s*$", "").trim();
                if (line.isBlank() || line.equalsIgnoreCase("Ruhetag")) continue;
                if (line.toLowerCase(DE).contains("mittagstisch")) continue;
                dishes.add(line);
            }
            if (dishes.size() == 2) {
                result.add(new DayMenu(date, dishes.get(0), dishes.get(1)));
            }
        }
        return List.copyOf(result);
    }

    private static LocalDate parseDate(String day, String monthName, String explicitYear, LocalDate referenceDate) {
        int month = switch (monthName.toLowerCase(DE)) {
            case "januar" -> 1; case "februar" -> 2; case "märz", "maerz" -> 3;
            case "april" -> 4; case "mai" -> 5; case "juni" -> 6; case "juli" -> 7;
            case "august" -> 8; case "september" -> 9; case "oktober" -> 10;
            case "november" -> 11; case "dezember" -> 12;
            default -> throw new DateTimeParseException("Unbekannter Monat", monthName, 0);
        };
        int year = explicitYear == null ? referenceDate.getYear() : Integer.parseInt(explicitYear);
        LocalDate date = LocalDate.of(year, month, Integer.parseInt(day));
        if (explicitYear == null && date.isAfter(referenceDate.plusDays(30))) date = date.minusYears(1);
        if (explicitYear == null && date.isBefore(referenceDate.minusDays(330))) date = date.plusYears(1);
        return date;
    }

    public record DayMenu(LocalDate date, String mainDish, String vegetarianDish) {}
}
