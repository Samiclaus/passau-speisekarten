package de.passau.speisekarten.wilmawunder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WilmaWunderMenuParserTest
{
    private final WilmaWunderMenuParser parser = new WilmaWunderMenuParser();

    @Test
    void parsesPricedArticleItemsAndIgnoresItemsWithoutPrice()
    {
        String html = """
            <div class="menu-section">
              <div><h3>Mittagsangebot</h3><p>Wochenangebot von Montag bis Freitag, außer an Feiertagen</p></div>
              <div class="articleitem">
                <h4 id="article-101">Unbrauchbarer technischer Titel</h4>
                <small class="article-description">Gemüse-Lasagne</small>
                <div class="articleitem_price">8,90 €</div>
              </div>
              <div class="articleitem">
                <h4 id="article-102">Anderer technischer Titel</h4>
                <small class="article-description">Pasta mit Hackfleisch</small>
                <div class="articleitem_price">9.50 €</div>
              </div>
              <div class="articleitem">
                <h4 id="article-103">Ohne Preis</h4>
                <small class="article-description">Tischlein deck dich</small>
                <div class="articleitem_price"></div>
              </div>
            </div>
            """;

        List<WilmaWunderMenuParser.Dish> dishes = parser.parse(html);

        assertEquals(2, dishes.size());
        assertEquals("Gemüse-Lasagne", dishes.get(0).name());
        assertEquals(8.90, dishes.get(0).price(), 0.001);
        assertEquals("Pasta mit Hackfleisch", dishes.get(1).name());
        assertEquals(9.50, dishes.get(1).price(), 0.001);
    }

    @Test
    void failsClosedWhenLunchOfferSectionIsMissing()
    {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("<html><h3>Abendkarte</h3></html>"));
    }
}
