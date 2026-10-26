package de.passau.speisekarten.mensa;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MensaHtmlResponseParserTest
{
    private final MensaHtmlResponseParser parser = new MensaHtmlResponseParser();

    @Test
    void parsesCategoryDishMarkersAndAllThreePrices()
    {
        String html = """
            <div class='spl'><table class='speiseplan-neu'>
              <tr class='gruppenkopf'><td class='gk'>Hauptgerichte</td></tr>
              <tr class='essenzeile'>
                <td></td><td class='cell1'>Blumenkohlröschen auf Gerstoni <sup>(3,AC,I)</sup></td>
                <td class='cell2'><div class='tooltip-container'><img class='icon' alt='Vegan'><span class='tooltip'>Vegan</span></div></td>
                <td class='preis'>2,30 &euro;</td><td class='preis'>3,30 &euro;</td><td class='preis'>6,10 &euro;</td>
              </tr>
              <tr class='essenzeile'>
                <td></td><td class='cell1'>Vollkorn Pasta, Jura-Distl Lammfleisch, Auberginen <sup>(16,AA,C)</sup></td>
                <td class='cell2'><img class='icon' alt='Lamm'><img class='icon' alt='Juradistl Lamm'><img class='icon' alt='Alkohol'></td>
                <td class='preis'>5,50 &euro;</td><td class='preis'>6,70 &euro;</td><td class='preis'>8,20 &euro;</td>
              </tr>
            </table></div>
            """;

        List<MensaHtmlDish> dishes = parser.parse(html);
        assertEquals(2, dishes.size());
        MensaHtmlDish vegan = dishes.get(0);
        assertEquals("Hauptgerichte", vegan.category());
        assertEquals("Blumenkohlröschen auf Gerstoni", vegan.name());
        assertTrue(vegan.markers().contains(MensaMarker.VEGAN));
        assertTrue(vegan.markers().contains(MensaMarker.VEGETARIAN));
        assertEquals(new BigDecimal("2.30"), vegan.studentPrice());
        assertEquals(new BigDecimal("3.30"), vegan.employeePrice());
        assertEquals(new BigDecimal("6.10"), vegan.guestPrice());

        MensaHtmlDish lamb = dishes.get(1);
        assertTrue(lamb.markers().contains(MensaMarker.LAMB));
        assertTrue(lamb.markers().contains(MensaMarker.JURADISTL));
        assertTrue(lamb.markers().contains(MensaMarker.CONTAINS_ALCOHOL));
        assertEquals("Vollkorn Pasta, Jura-Distl Lammfleisch, Auberginen", lamb.name());
    }

    @Test
    void skipsHeadingsAndEmptyDishRowsAndHandlesMissingPrices()
    {
        String html = """
            <table>
              <tr class='gruppenkopf'><td class='gk'>Suppen</td></tr>
              <tr class='essenzeile'><td class='cell1'>   </td></tr>
              <tr class='essenzeile'><td class='cell1'>Steinpilzcremesuppe <sup>(3,G,I)</sup></td><td class='cell2'><img alt='vegetarisch'></td><td class='preis'>0,80 €</td></tr>
            </table>
            """;
        List<MensaHtmlDish> dishes = parser.parse(html);
        assertEquals(1, dishes.size());
        assertEquals("Suppen", dishes.get(0).category());
        assertEquals(new BigDecimal("0.80"), dishes.get(0).studentPrice());
        assertNull(dishes.get(0).employeePrice());
        assertNull(dishes.get(0).guestPrice());
    }

    @Test
    void emptyInputProducesNoDishes()
    {
        assertTrue(parser.parse(" ").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }
}
