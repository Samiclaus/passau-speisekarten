package de.passau.speisekarten.pomeranz;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class PomeranzSourceTest {
    @Test void findsPdfFromMittagskarteLabelAndResolvesRelativeUrl() {
        var source = new PomeranzSource();
        URI uri = source.findCurrentPdf("""
            <a href="/.cm4all/mediadb/2026-10-20-Mittagskarte.pdf">Mittagskarte</a>
            <a href="/abendkarte.pdf">Abendkarte</a>
            """, URI.create("https://www.restaurant-pomeranz.de/Speisekarte/"));
        assertEquals("https://www.restaurant-pomeranz.de/.cm4all/mediadb/2026-10-20-Mittagskarte.pdf", uri.toString());
    }

    @Test void prefersNewestDatedLunchCardWhenPageContainsSeveral() {
        var source = new PomeranzSource();
        URI uri = source.findCurrentPdf("""
            <a href="/.cm4all/mediadb/2026-10-13-Mittagskarte.pdf">Mittagskarte</a>
            <a href="/.cm4all/mediadb/2026-10-27-Mittagskarte.pdf">Mittagskarte</a>
            <a href="/.cm4all/mediadb/2026-10-20-Mittagskarte.pdf">Mittagskarte</a>
            """, URI.create("https://www.restaurant-pomeranz.de/Speisekarte/"));
        assertTrue(uri.toString().contains("2026-10-27-Mittagskarte.pdf"));
    }

    @Test void failsClearlyWhenNoLunchCardExists() {
        assertThrows(IllegalArgumentException.class, () -> new PomeranzSource().findCurrentPdf(
            "<a href='karte.pdf'>Speisekarte</a>", URI.create("https://example.org/")));
    }
}
