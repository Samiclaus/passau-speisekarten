package de.passau.speisekarten.pomeranz;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the newest dated PDF labelled as the current lunch card. */
public final class PomeranzSource {
    public static final URI MENU_PAGE = URI.create("https://www.restaurant-pomeranz.de/Speisekarte/");

    public URI findCurrentPdf(String html, URI pageUri) {
        Document doc = Jsoup.parse(html, pageUri.toString());
        return doc.select("a[href]")
            .stream()
            // The date prefix changes; the stable filename token is Mittagskarte.pdf.
            .map(a -> pageUri.resolve(a.attr("href")))
            .filter(uri -> uri.getPath() != null
                && uri.getPath().matches("(?i).*Mittagskarte\\.pdf$"))
            .max(Comparator.comparing(PomeranzSource::dateFromFilename, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(URI::toString))
            .orElseThrow(() -> new IllegalArgumentException("No PDF link labelled Mittagskarte found"));
    }

    private static LocalDate dateFromFilename(URI uri) {
        Matcher matcher = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})").matcher(uri.getPath());
        if (!matcher.find()) return null;
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    public String fetchPage() throws IOException {
        return Jsoup.connect(MENU_PAGE.toString()).userAgent("PassauSpeisekarten/1.0")
            .timeout(15000).get().html();
    }
}
