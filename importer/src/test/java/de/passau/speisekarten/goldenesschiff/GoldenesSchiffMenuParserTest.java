package de.passau.speisekarten.goldenesschiff;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GoldenesSchiffMenuParserTest {
    @Test void parsesDatedMainAndVegetarianDishPairsAndKeepsHistoricalDates() {
        String pdf = """
            Mittagstisch
            Montag, 05. Oktober
            Ruhetag
            Dienstag, 06. Oktober
            Schnitzel Wiener Art mit Kartoffelsalat
            Rahmpfifferling mit hausgemachten Semmelknödel
            Mittwoch, 07. Oktober
            Asiatische Gemüsereispfanne mit Putenstreifen
            Rahmpfifferling mit hausgemachten Semmelknödel
            """;
        var result = new GoldenesSchiffMenuParser().parse(pdf, LocalDate.of(2026, 10, 26));
        assertEquals(2, result.size());
        assertEquals(LocalDate.of(2026, 10, 6), result.get(0).date());
        assertEquals("Schnitzel Wiener Art mit Kartoffelsalat", result.get(0).mainDish());
        assertEquals("Rahmpfifferling mit hausgemachten Semmelknödel", result.get(0).vegetarianDish());
    }
}
