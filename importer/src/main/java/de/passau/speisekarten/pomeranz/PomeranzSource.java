package de.passau.speisekarten.pomeranz;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Finds the newest dated PDF labelled as the current lunch card. */
public final class PomeranzSource {
    public static final URI MENU_PAGE = URI.create("https://www.restaurant-pomeranz.de/Speisekarte/");

    public URI findCurrentPdf(String html, URI pageUri) {
        Document doc = Jsoup.parse(html, pageUri.toString());
        return doc.select("a[href]")
            .stream()
            .filter(PomeranzSource::isPdfLink)
            .filter(PomeranzSource::isLunchCardLink)
            .map(a -> pageUri.resolve(a.attr("href")))
            .filter(uri -> uri.getPath() != null && uri.getPath().toLowerCase().endsWith(".pdf"))
            .max(Comparator.comparing(PomeranzSource::dateFromFilename, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(URI::toString))
            .orElseThrow(() -> new IllegalArgumentException(
                "No PDF link for the Pomeranz Mittagskarte found. Check the live page HTML or its link labels."));
    }

    private static boolean isPdfLink(Element link) {
        String href = link.attr("href").toLowerCase();
        return href.matches("(?s).*\\.pdf(?:[?#].*)?$");
    }

    private static boolean isLunchCardLink(Element link) {
        String href = link.attr("href");
        String label = link.text() + " " + link.attr("title") + " "
            + link.attr("aria-label") + " " + link.select("img[alt]").eachAttr("alt");
        return href.matches("(?is).*Mittagskarte\\.pdf(?:[?#].*)?$")
            || label.matches("(?is).*Mittagskarte.*");
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
        return Jsoup.connect(MENU_PAGE.toString()).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/155.0.0.0 Safari/537.36")
            .timeout(15000).get().html();
    }
}
