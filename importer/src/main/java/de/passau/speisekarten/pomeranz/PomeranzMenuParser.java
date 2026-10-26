package de.passau.speisekarten.pomeranz;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts metadata and the weekly vegetarian plus weekday-specific dishes from Pomeranz's lunch PDF. */
public final class PomeranzMenuParser {
    private static final Locale DE = Locale.GERMAN;
    private static final Pattern RANGE = Pattern.compile("(?i)(?:für\\s+den\\s+)?(\\d{1,2})\\.\\s*(?:bis\\s*)?(\\d{1,2})?\\.?\\s*(Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)(?:\\s+(20\\d{2}))?");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}:\\d{2})\\s*(?:bis|[-–])\\s*(\\d{1,2}:\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile("(?:€\\s*(\\d{1,3}[,.]\\d{2})|(\\d{1,3}[,.]\\d{2})\\s*€)");
    private static final Pattern WEEKDAY = Pattern.compile("(?i)\\b(Montag|Dienstag|Mittwoch|Donnerstag|Freitag|Samstag|Sonntag)\\b");
    private static final Pattern FILENAME_DATE = Pattern.compile("(20\\d{2})-(\\d{2})-(\\d{2})");

    public PomeranzMenu parse(Path pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf.toFile())) {
            return parse(new PDFTextStripper().getText(document), pdf.getFileName().toString());
        }
    }

    public PomeranzMenu parse(InputStream pdf, String fileName) throws IOException {
        try (var document = Loader.loadPDF(pdf.readAllBytes())) {
            return parse(new PDFTextStripper().getText(document), fileName);
        }
    }

    public PomeranzMenu parse(String text, String fileName) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("PDF contains no extractable text");
        String normalized = text.replace('\r', '\n').replaceAll("[ \\t]+", " ");
        LocalDate filenameDate = filenameDate(fileName);
        LocalDate start = null;
        LocalDate end = null;
        Matcher range = RANGE.matcher(normalized.replaceAll("\\s+", " "));
        if (range.find()) {
            int first = Integer.parseInt(range.group(1));
            int last = range.group(2) == null ? first : Integer.parseInt(range.group(2));
            int month = month(range.group(3));
            int year = range.group(4) != null ? Integer.parseInt(range.group(4))
                : filenameDate != null ? filenameDate.getYear() : LocalDate.now().getYear();
            start = LocalDate.of(year, month, first);
            end = LocalDate.of(year, month, last);
        } else if (filenameDate != null) {
            start = filenameDate;
            end = filenameDate;
        }
        if (start == null) throw new IllegalArgumentException("Could not determine menu date range from PDF text or filename");
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 13)
            throw new IllegalArgumentException("Invalid or implausibly long menu date range");

        Matcher time = TIME.matcher(normalized);
        String from = null, to = null;
        if (time.find()) { from = time.group(1); to = time.group(2); }

        BigDecimal comboPrice = null;
        Matcher price = PRICE.matcher(normalized);
        while (price.find()) {
            String before = normalized.substring(Math.max(0, price.start() - 100), price.start()).toLowerCase(DE);
            if (before.contains("komb") || before.contains("angebot") || before.contains("menü") || before.contains("menu")) {
                String amount = price.group(1) != null ? price.group(1) : price.group(2);
                comboPrice = new BigDecimal(amount.replace(',', '.'));
                break;
            }
        }
        if (comboPrice == null && normalized.toLowerCase(DE).contains("schnelle nummer zwei für eins")) {
            Matcher amount = Pattern.compile("€\\s*(\\d{1,3}[,.]\\d{2})").matcher(normalized);
            if (amount.find()) comboPrice = new BigDecimal(amount.group(1).replace(',', '.'));
        }

        List<String> lines = normalized.lines().map(String::trim).filter(s -> !s.isBlank()).toList();
        String vegetarian = null;
        String vegetarianDescription = null;
        List<PomeranzDay> days = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String lower = lines.get(i).toLowerCase(DE);
            if (lower.contains("vegetarisches hauptgericht der woche") ||
                lower.contains("vegetarisches wochenangebot") || lower.contains("vegetarisches wochengericht")) {
                if (i + 1 < lines.size()) vegetarian = cleanDish(lines.get(++i));
                if (i + 1 < lines.size() && isDescription(lines.get(i + 1))) vegetarianDescription = cleanDish(lines.get(++i));
                continue;
            }

            Matcher weekday = WEEKDAY.matcher(lines.get(i));
            if (lower.startsWith("am ") && lower.contains(" in kombination mit") && weekday.find()) {
                DayOfWeek dayOfWeek = parseWeekday(weekday.group(1));
                LocalDate date = dateFor(start, end, dayOfWeek);
                if (date == null) continue;
                String dish = i + 1 < lines.size() ? cleanDish(lines.get(++i)) : null;
                String description = null;
                if (i + 1 < lines.size() && isDescription(lines.get(i + 1))) description = cleanDish(lines.get(++i));
                days.add(new PomeranzDay(date, dish, description, vegetarian, vegetarianDescription));
            }
        }

        return new PomeranzMenu(start, end, from, to, comboPrice, vegetarian, vegetarianDescription,
            List.copyOf(days), text);
    }

    private static LocalDate filenameDate(String fileName) {
        Matcher matcher = FILENAME_DATE.matcher(fileName == null ? "" : fileName);
        return matcher.find() ? LocalDate.parse(matcher.group(), DateTimeFormatter.ISO_LOCAL_DATE) : null;
    }

    private static LocalDate dateFor(LocalDate start, LocalDate end, DayOfWeek day) {
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == day) return date;
        }
        return null;
    }

    private static DayOfWeek parseWeekday(String value) {
        return switch (value.toLowerCase(DE)) {
            case "montag" -> DayOfWeek.MONDAY;
            case "dienstag" -> DayOfWeek.TUESDAY;
            case "mittwoch" -> DayOfWeek.WEDNESDAY;
            case "donnerstag" -> DayOfWeek.THURSDAY;
            case "freitag" -> DayOfWeek.FRIDAY;
            case "samstag" -> DayOfWeek.SATURDAY;
            case "sonntag" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown weekday: " + value);
        };
    }

    private static boolean isDescription(String value) {
        String s = value.toLowerCase(DE);
        return !s.startsWith("am ") && !s.contains("vegetarisches hauptgericht") &&
            !s.contains("mittagskarte") && !s.contains("suppe:") && !s.contains("vorspeise:") &&
            !s.contains("dessert:") && !s.contains("angebot gilt") && !s.contains("uhr") &&
            !s.contains("kombination mit") && !s.matches(".*\\d{1,2}:\\d{2}.*");
    }

    private static String cleanDish(String value) {
        return value.replaceAll("\\s+[0-9]+(?:,[0-9]+)*\\s*$", "").trim();
    }

    private static int month(String value) {
        return switch (value.toLowerCase(DE)) {
            case "januar" -> 1; case "februar" -> 2; case "märz", "maerz" -> 3;
            case "april" -> 4; case "mai" -> 5; case "juni" -> 6; case "juli" -> 7;
            case "august" -> 8; case "september" -> 9; case "oktober" -> 10;
            case "november" -> 11; case "dezember" -> 12;
            default -> throw new DateTimeParseException("Unknown German month", value, 0);
        };
    }

    public record PomeranzMenu(LocalDate startDate, LocalDate endDate, String serviceStart, String serviceEnd,
                               BigDecimal comboPrice, String vegetarianDish, String vegetarianDescription,
                               List<PomeranzDay> days, String extractedText) {}
    public record PomeranzDay(LocalDate date, String dishText, String dishDescription,
                              String vegetarianDish, String vegetarianDescription) {}
}
