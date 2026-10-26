package de.passau.speisekarten.saporitoscana;

import de.passau.speisekarten.mensa.MensaMarker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SaporiToscanaMenuParserTest
{
    private final SaporiToscanaMenuParser parser = new SaporiToscanaMenuParser();

    @Test
    void parsesDishNamePriceAndOptionalDescriptionFromWeekdayBlocks()
    {
        String html = """
            <div class="weekday">
              <h3>Montag</h3>
              <p><strong>Spaghettoni Nduja</strong></p>
              <p>€ 13.00</p>
              <p>Zwiebeln, Pomodoro, Nduja, Öl</p>
              <p><strong>Malfatti Ricotta – Spinat</strong></p>
              <p>€ 12.50</p>
              <p>Spinat-Ricotta-Nocken</p>
            </div>
            <div class="weekday">
              <h3>Dienstag</h3>
              <p><strong>Farfale con panna e salmone</strong></p>
              <p>€ 15.00</p>
              <p>Butter, Sahne, Lachsfilet</p>
              <p><strong>Fagioli con Salsicia Italiana</strong></p>
              <p>€ 14.00</p>
              <p>Zwiebeln, Karotten, Pomodori, Salsicia, Sellerie</p>
            </div>
            """;

        List<SaporiToscanaMenuParser.SaporiToscanaDish> dishes = parser.parse(html);

        assertEquals(4, dishes.size());
        assertEquals("Spaghettoni Nduja", dishes.get(0).name());
        assertEquals(new BigDecimal("13.00"), dishes.get(0).price());
        assertEquals("Zwiebeln, Pomodoro, Nduja, Öl", dishes.get(0).description());
        assertTrue(dishes.get(0).markers().contains(MensaMarker.PORK));
        assertTrue(dishes.get(1).markers().contains(MensaMarker.VEGETARIAN));
        assertTrue(dishes.get(2).markers().contains(MensaMarker.FISH));
        assertTrue(dishes.get(3).markers().contains(MensaMarker.PORK));
    }

    @Test
    void detectsGenericMeatAndDoesNotMarkItVegetarian()
    {
        String html = """
            <div><h3>Donnerstag</h3>
              <p><strong>Tortelini Di carne in brodo Vegetale</strong></p>
              <p>€ 12.00</p>
              <p>Fleischfüllung</p>
              <p><strong>Penne al Ragu</strong></p>
              <p>€ 13.00</p>
              <p>Tomatensauce</p>
            </div>
            """;

        List<SaporiToscanaMenuParser.SaporiToscanaDish> dishes = parser.parse(html);

        assertEquals(2, dishes.size());
        assertTrue(dishes.get(0).markers().contains(MensaMarker.MEAT));
        assertFalse(dishes.get(0).markers().contains(MensaMarker.VEGETARIAN));
        assertTrue(dishes.get(1).markers().contains(MensaMarker.MEAT));
        assertFalse(dishes.get(1).markers().contains(MensaMarker.VEGETARIAN));
    }

    @Test
    void supportsPriceWithoutSpaceAndCommaDecimal()
    {
        String html = """
            <div><h3>Freitag</h3>
              <p><strong>Fussili con Tonno Limone</strong></p>
              <p>13,80€</p>
              <p>Öl, Butter, Kapern, Tonno, Zitronen</p>
            </div>
            """;
        var dishes = parser.parse(html);
        assertEquals(1, dishes.size());
        assertEquals(new BigDecimal("13.80"), dishes.get(0).price());
        assertTrue(dishes.get(0).markers().contains(MensaMarker.FISH));
    }
}
