package de.passau.speisekarten.pomeranz;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.net.URI;
import java.util.Comparator;

/** Finds the current PDF by the semantic "Mittagskarte" link, not by a date-specific URL. */
public final class PomeranzSource {
    public static final URI MENU_PAGE = URI.create("https://www.restaurant-pomeranz.de/Speisekarte/");

    public URI findCurrentPdf(String html, URI pageUri) {
        Document doc = Jsoup.parse(html, pageUri.toString());
        return doc.select("a[href]")
            .stream()
            .filter(a -> (a.text() + " " + a.attr("href")).toLowerCase().contains("mittagskarte"))
            .map(a -> pageUri.resolve(a.attr("href")))
            .filter(uri -> uri.getPath() != null && uri.getPath().toLowerCase().endsWith(".pdf"))
            .max(Comparator.comparing(URI::toString))
            .orElseThrow(() -> new IllegalArgumentException("No PDF link labelled Mittagskarte found"));
    }

    public String fetchPage() throws IOException {
        return Jsoup.connect(MENU_PAGE.toString()).userAgent("PassauSpeisekarten/1.0")
            .timeout(15000).get().html();
    }
}
