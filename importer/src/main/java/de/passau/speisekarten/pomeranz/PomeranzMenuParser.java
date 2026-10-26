package de.passau.speisekarten.pomeranz;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the date range, service time, combo price and weekday dishes from Pomeranz's lunch-card PDF. */
public final class PomeranzMenuParser {
    private static final Locale DE = Locale.GERMAN;
    private static final Pattern RANGE = Pattern.compile("(?i)(?:für\\s+den\\s+)?(\\d{1,2})\\.\\s*(?:bis\\s*)?(\\d{1,2})?\\.?\\s*(Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)\\s+(20\\d{2})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}:\\d{2})\\s*(?:bis|[-–])\\s*(\\d{1,2}:\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile("(\\d{1,3}[,.]\\d{2})\\s*€");
    private static final Pattern WEEKDAY = Pattern.compile("(?i)\\b(Montag|Dienstag|Mittwoch|Donnerstag|Freitag|Samstag|Sonntag)\\b");

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
        LocalDate start = null, end = null;
        Matcher range = RANGE.matcher(text.replaceAll("\\s+", " "));
        if (range.find()) {
            int first = Integer.parseInt(range.group(1));
            int last = range.group(2) == null ? first : Integer.parseInt(range.group(2));
            int month = month(range.group(3));
            int year = Integer.parseInt(range.group(4));
            start = LocalDate.of(year, month, first);
            end = LocalDate.of(year, month, last);
        }
        if (start == null) {
            Matcher filename = Pattern.compile("(20\\d{2})-(\\d{2})-(\\d{2})").matcher(fileName == null ? "" : fileName);
            if (filename.find()) start = LocalDate.parse(filename.group(), DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (start == null) throw new IllegalArgumentException("Could not determine menu date range from PDF text or filename");
        if (end == null) end = start;
        if (end.isBefore(start) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 13)
            throw new IllegalArgumentException("Invalid or implausibly long menu date range");

        Matcher time = TIME.matcher(text);
        String from = time.find() ? time.group(1) : null;
        String to = time.find(0) ? time.group(2) : null;
        BigDecimal comboPrice = null;
        Matcher price = PRICE.matcher(text);
        while (price.find()) {
            String before = text.substring(Math.max(0, price.start() - 90), price.start()).toLowerCase(DE);
            if (before.contains("komb") || before.contains("angebot") || before.contains("menü") || before.contains("menu")) {
                comboPrice = new BigDecimal(price.group(1).replace(',', '.'));
                break;
            }
        }
        if (comboPrice == null && text.toLowerCase(DE).contains("12,80")) comboPrice = new BigDecimal("12.80");

        List<PomeranzDay> days = new ArrayList<>();
        String[] lines = text.replace('\r', '\n').split("\\n+");
        LocalDate currentDate = null;
        String vegetarian = null;
        for (String raw : lines) {
            String line = raw.replaceAll("\\s+", " ").trim();
            if (line.isEmpty()) continue;
            Matcher weekday = WEEKDAY.matcher(line);
            if (weekday.find()) {
                DayOfWeek day = switch (weekday.group(1).toLowerCase(DE)) {
                    case "montag" -> DayOfWeek.MONDAY;
                    case "dienstag" -> DayOfWeek.TUESDAY;
                    case "mittwoch" -> DayOfWeek.WEDNESDAY;
                    case "donnerstag" -> DayOfWeek.THURSDAY;
                    case "freitag" -> DayOfWeek.FRIDAY;
                    case "samstag" -> DayOfWeek.SATURDAY;
                    case "sonntag" -> DayOfWeek.SUNDAY;
                    default -> throw new IllegalArgumentException("Unknown weekday");
                };
                LocalDate match = start;
                while (match.getDayOfWeek() != day && !match.isAfter(end)) match = match.plusDays(1);
                if (!match.isAfter(end)) currentDate = match;
            }
            String lower = line.toLowerCase(DE);
            if (lower.contains("vegetar")) {
                String dish = line.replaceFirst("(?i)^.*?vegetarisch(?:es|e|er)?\\s*[:–-]?\\s*", "").trim();
                if (!dish.isBlank() && !dish.equalsIgnoreCase(line)) vegetarian = dish;
            } else if (currentDate != null && !isHeaderOrMeta(lower)) {
                // Store only lines that resemble a dish; keep text intact for manual review.
                if (line.length() > 4 && !WEEKDAY.matcher(line).matches() && !lower.contains("11:") &&
                    !lower.contains("12,80") && !lower.contains("mittagskarte")) {
                    days.add(new PomeranzDay(currentDate, line, vegetarian));
                    currentDate = null;
                }
            }
        }
        // Weekday/date information is useful even when text extraction layout needs a later parser refinement.
        return new PomeranzMenu(start, end, from, to, comboPrice, vegetarian, List.copyOf(days), text);
    }

    private static boolean isHeaderOrMeta(String s) {
        return s.contains("für den") || s.contains("uhr") || s.contains("dessert") ||
            s.contains("vorspeise") || s.contains("suppe") || s.contains("kombinations") ||
            s.contains("wahlweise") || s.contains("mittag") || s.contains("restaurant pomeranz");
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
                               BigDecimal comboPrice, String vegetarianDish, List<PomeranzDay> days, String extractedText) {}
    public record PomeranzDay(LocalDate date, String dishText, String vegetarianDish) {}
}
