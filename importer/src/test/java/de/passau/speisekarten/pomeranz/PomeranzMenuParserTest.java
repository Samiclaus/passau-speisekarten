package de.passau.speisekarten.pomeranz;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PomeranzMenuParserTest {
    @Test void readsDateRangeHoursAndComboPrice() {
        String text = """
            Mittagskarte
            für den 20. bis 23. Oktober 2026 von 11:30 bis 14:00 Uhr
            Schnelle Nummer Zwei für Eins € 12,80
            Vegetarisches Hauptgericht der Woche
            Gemüse-Curry 7
            Frische Kräuter / Reis
            Am Dienstag in Kombination mit
            Rinderbraten 1,7
            Kartoffeln / Gemüse
            """;
        var menu = new PomeranzMenuParser().parse(text, "2026-10-20-Mittagskarte.pdf");
        assertEquals(LocalDate.of(2026, 10, 20), menu.startDate());
        assertEquals(LocalDate.of(2026, 10, 23), menu.endDate());
        assertEquals("11:30", menu.serviceStart());
        assertEquals("14:00", menu.serviceEnd());
        assertEquals(new BigDecimal("12.80"), menu.comboPrice());
        assertEquals("Gemüse-Curry", menu.vegetarianDish());
        assertEquals("Frische Kräuter / Reis", menu.vegetarianDescription());
        assertEquals(1, menu.days().size());
        assertEquals(LocalDate.of(2026, 10, 20), menu.days().get(0).date());
        assertEquals("Rinderbraten", menu.days().get(0).dishText());
        assertEquals("Gemüse-Curry", menu.days().get(0).vegetarianDish());
    }

    @Test void parsesRealisticPomeranzPdfTextWithoutYearAndAllergyNumbers() {
        String text = """
            MITTAGSKARTE
            für den 20. bis 23. Oktober von 11:30 bis 14:00 Uhr
            Schnelle Nummer Zwei für Eins € 12,80
            Das Angebot gilt nur in Verbindung mit mindestens einem Getränk.
            Suppe: Kürbiscremesuppe mit Kresse 7
            oder
            Vorspeise: Tomaten-Aufstrich mit Brot 1,7
            oder
            Dessert: Profiterole mit Mango 1,3,7
            Vegetarisches Hauptgericht der Woche
            Pilz-Risotto 7
            Frische Tomaten / Lauchzwiebeln / Grana Padano
            Am Dienstag in Kombination mit
            Penne con Filetto 1,3,7
            Schweinefiletspitzen / Brokkoli / Tomaten
            Am Mittwoch in Kombination mit
            Gebratenem Hähnchenbrustfilet 1,7
            Zucchini / Graupen / Kräuterschaum
            Am Donnerstag in Kombination mit
            Spaghetti Pesce 1,4,7,14
            Fischfilet / Calamari / Wurzelgemüse
            Am Freitag in Kombination mit
            Buttermakrelenfilet 4,7,8
            Pilze / Risotto / Pesto
            """;
        var menu = new PomeranzMenuParser().parse(text, "2026-10-20-Mittagskarte.pdf");
        assertEquals(LocalDate.of(2026, 10, 20), menu.startDate());
        assertEquals(LocalDate.of(2026, 10, 23), menu.endDate());
        assertEquals(new BigDecimal("12.80"), menu.comboPrice());
        assertEquals("11:30", menu.serviceStart());
        assertEquals("14:00", menu.serviceEnd());
        assertEquals("Pilz-Risotto", menu.vegetarianDish());
        assertEquals(List.of(
            "Suppe: Kürbiscremesuppe mit Kresse",
            "Vorspeise: Tomaten-Aufstrich mit Brot",
            "Dessert: Profiterole mit Mango"), menu.optionalComponents());
        assertEquals(4, menu.days().size());
        assertEquals(LocalDate.of(2026, 10, 20), menu.days().get(0).date());
        assertEquals("Penne con Filetto", menu.days().get(0).dishText());
        assertEquals("Schweinefiletspitzen / Brokkoli / Tomaten", menu.days().get(0).dishDescription());
        assertEquals(LocalDate.of(2026, 10, 21), menu.days().get(1).date());
        assertEquals("Gebratenem Hähnchenbrustfilet", menu.days().get(1).dishText());
        assertEquals("Buttermakrelenfilet", menu.days().get(3).dishText());
    }

    @Test void doesNotTreatNextWeekdayHeadingAsMissingDish() {
        String text = """
            Mittagskarte
            für den 20. bis 23. Oktober von 11:30 bis 14:00 Uhr
            Schnelle Nummer Zwei für Eins € 12,80
            Vegetarisches Hauptgericht der Woche
            Pilz-Risotto 7
            Am Dienstag in Kombination mit
            Am Mittwoch in Kombination mit
            Gebratenes Hähnchenbrustfilet 1,7
            Zucchini / Graupen / Kräuterschaum
            """;
        var menu = new PomeranzMenuParser().parse(text, "2026-10-20-Mittagskarte.pdf");

        assertEquals(2, menu.days().size());
        assertNull(menu.days().get(0).dishText());
        assertEquals(LocalDate.of(2026, 10, 21), menu.days().get(1).date());
        assertEquals("Gebratenes Hähnchenbrustfilet", menu.days().get(1).dishText());
        assertEquals("Zucchini / Graupen / Kräuterschaum", menu.days().get(1).dishDescription());
    }

    @Test void fallsBackToFilenameDateWhenTextOmitsRange() {
        var menu = new PomeranzMenuParser().parse("Mittagskarte", "2026-10-20-Mittagskarte.pdf");
        assertEquals(LocalDate.of(2026, 10, 20), menu.startDate());
        assertEquals(menu.startDate(), menu.endDate());
    }
}
