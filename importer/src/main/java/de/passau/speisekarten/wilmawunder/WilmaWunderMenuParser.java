package de.passau.speisekarten.wilmawunder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser for the AmadeusGo weekly lunch offer embedded in Wilma Wunder's menu page. */
public final class WilmaWunderMenuParser
{
    private static final Pattern PRICE = Pattern.compile("(?<!\\d)(\\d{1,3}(?:[.,]\\d{2}))(?:\\s*€)?");

    public List<Dish> parse(String html)
    {
        Document document = Jsoup.parse(html);
        Element heading = document.select("h3").stream()
            .filter(element -> normalize(element.text()).equals("mittagsangebot"))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("AmadeusGo response has no 'Mittagsangebot' heading."));

        Element section = heading;
        while (section.parent() != null)
        {
            section = section.parent();
            if (!section.select("div.articleitem").isEmpty())
            {
                break;
            }
        }

        if (section.select("div.articleitem").isEmpty())
        {
            throw new IllegalArgumentException("No articleitem entries found inside the Mittagsangebot section.");
        }

        List<Dish> dishes = new ArrayList<>();
        for (Element article : section.select("div.articleitem"))
        {
            String name = article.select("small.article-description").text().trim();
            if (name.isBlank())
            {
                name = article.text().trim();
            }

            String priceText = article.select("div.articleitem_price").text().trim();
            Matcher matcher = PRICE.matcher(priceText);
            if (name.isBlank() || !matcher.find())
            {
                continue;
            }

            double price;
            try
            {
                price = Double.parseDouble(matcher.group(1).replace(',', '.'));
            }
            catch (NumberFormatException exception)
            {
                continue;
            }
            dishes.add(new Dish(name, price));
        }

        if (dishes.isEmpty())
        {
            throw new IllegalArgumentException("Mittagsangebot section contained no dishes with a readable price.");
        }
        return List.copyOf(dishes);
    }

    private static String normalize(String value)
    {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    public record Dish(String name, double price) {}
}
