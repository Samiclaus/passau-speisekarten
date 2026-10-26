package de.passau.speisekarten.mensa;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parses the HTML fragment returned by the STWNO menu-render POST endpoint. */
public final class MensaHtmlResponseParser
{
    private static final DishMarkerClassifier DISH_MARKER_CLASSIFIER = new DishMarkerClassifier();
    private static final Map<String, MensaMarker> MARKERS = Map.ofEntries(
        Map.entry("vegetarisch", MensaMarker.VEGETARIAN),
        Map.entry("vegan", MensaMarker.VEGAN),
        Map.entry("mensavital", MensaMarker.MENSA_VITAL),
        Map.entry("mensa vital", MensaMarker.MENSA_VITAL),
        Map.entry("geflügel", MensaMarker.POULTRY),
        Map.entry("gefluegel", MensaMarker.POULTRY),
        Map.entry("glutenfrei", MensaMarker.GLUTEN_FREE),
        Map.entry("gluten-free", MensaMarker.GLUTEN_FREE),
        Map.entry("rind", MensaMarker.BEEF),
        Map.entry("bio-gericht", MensaMarker.ORGANIC),
        Map.entry("bio", MensaMarker.ORGANIC),
        Map.entry("schwein", MensaMarker.PORK),
        Map.entry("alkohol", MensaMarker.CONTAINS_ALCOHOL),
        Map.entry("lamm", MensaMarker.LAMB),
        Map.entry("juradistl lamm", MensaMarker.JURADISTL),
        Map.entry("juradistl", MensaMarker.JURADISTL),
        Map.entry("wild", MensaMarker.GAME_MEAT),
        Map.entry("wildfleisch", MensaMarker.GAME_MEAT),
        Map.entry("fisch", MensaMarker.FISH)
    );

    public List<MensaHtmlDish> parse(String html)
    {
        if (html == null || html.isBlank())
        {
            return List.of();
        }

        Document document = Jsoup.parseBodyFragment(html);
        List<MensaHtmlDish> dishes = new ArrayList<>();
        String category = null;

        for (Element row : document.select("tr"))
        {
            if (row.hasClass("gruppenkopf"))
            {
                Element heading = row.selectFirst(".gk");
                if (heading != null && !heading.text().isBlank())
                {
                    category = heading.text().trim();
                }
                continue;
            }
            if (!row.hasClass("essenzeile"))
            {
                continue;
            }

            Element nameCell = row.selectFirst("td.cell1");
            if (nameCell == null)
            {
                continue;
            }

            // Remove the source's superscript allergen codes before normalization.
            nameCell.select("sup").remove();
            String name = MensaDishNameNormalizer.removeTrailingAllergenList(nameCell.text());
            if (name == null || name.isBlank())
            {
                continue;
            }

            EnumSet<MensaMarker> markers = EnumSet.noneOf(MensaMarker.class);
            for (Element icon : row.select("td.cell2 img.icon, td.cell2 .tooltip"))
            {
                String label = icon.hasAttr("alt") ? icon.attr("alt") : icon.text();
                MensaMarker marker = MARKERS.get(normalizeLabel(label));
                if (marker != null)
                {
                    markers.add(marker);
                }
            }
            markers.addAll(DISH_MARKER_CLASSIFIER.classify(name, ""));
            if (markers.contains(MensaMarker.VEGAN))
            {
                markers.add(MensaMarker.VEGETARIAN);
            }

            Elements prices = row.select("td.preis");
            dishes.add(new MensaHtmlDish(
                category,
                name,
                markers.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(markers)),
                priceAt(prices, 0),
                priceAt(prices, 1),
                priceAt(prices, 2)
            ));
        }

        return List.copyOf(dishes);
    }

    private static String normalizeLabel(String label)
    {
        return label == null ? "" : label.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static BigDecimal priceAt(Elements prices, int index)
    {
        if (prices.size() <= index)
        {
            return null;
        }
        String text = prices.get(index).text()
            .replace("€", "")
            .replace("\u00a0", "")
            .trim()
            .replace(".", "")
            .replace(',', '.');
        if (text.isBlank() || text.equals("-"))
        {
            return null;
        }
        try
        {
            return new BigDecimal(text);
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }
}
