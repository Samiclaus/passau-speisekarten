package de.passau.speisekarten.pomeranz;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class PomeranzMenuParserTest {
    @Test void readsDateRangeHoursAndComboPrice() {
        String text = """
            Mittagskarte
            für den 20. bis 23. Oktober 2026 von 11:30 bis 14:00 Uhr
            Kombi-Angebot 12,80 €
            Dienstag
            Vegetarisches Wochenangebot: Gemüse-Curry
            Tagesgericht: Rinderbraten
            """;
        var menu = new PomeranzMenuParser().parse(text, "2026-10-20-Mittagskarte.pdf");
        assertEquals(LocalDate.of(2026, 10, 20), menu.startDate());
        assertEquals(LocalDate.of(2026, 10, 23), menu.endDate());
        assertEquals("11:30", menu.serviceStart());
        assertEquals("14:00", menu.serviceEnd());
        assertEquals(new BigDecimal("12.80"), menu.comboPrice());
        assertEquals("Gemüse-Curry", menu.vegetarianDish());
    }

    @Test void fallsBackToFilenameDateWhenTextOmitsRange() {
        var menu = new PomeranzMenuParser().parse("Mittagskarte", "2026-10-20-Mittagskarte.pdf");
        assertEquals(LocalDate.of(2026, 10, 20), menu.startDate());
        assertEquals(menu.startDate(), menu.endDate());
    }
}