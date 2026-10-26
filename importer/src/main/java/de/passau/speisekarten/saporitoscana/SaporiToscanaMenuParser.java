package de.passau.speisekarten.saporitoscana;

import de.passau.speisekarten.mensa.MensaMarker;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses weekday blocks on i Sapori della Toscana's lunch menu. */
public final class SaporiToscanaMenuParser
{
    private static final Pattern WEEKDAY = Pattern.compile(
        "^\\s*(Montag|Dienstag|Mittwoch|Donnerstag|Freitag)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile(
        "(?i)(?:€\\s*|\\bEUR\\s*)(\\d{1,3}(?:[.,]\\s*\\d{1,2})?)|"
            + "(\\d{1,3}(?:[.,]\\s*\\d{1,2})?)\\s*€");
    private static final List<AnimalTerm> ANIMAL_TERMS = List.of(
        new AnimalTerm(MensaMarker.PORK, "\\b(?:nduja|salsiccia|salsicia|speck|maiale|porco|schwein\\w*|pancetta|prosciutto|salami|guanciale)\\b"),
        new AnimalTerm(MensaMarker.FISH, "\\b(?:salmone|salmon|lachs\\w*|tonno|thunfisch\\w*|fisch\\w*|pesce|sardellen|anchovis|anchovy)\\b"),
        new AnimalTerm(MensaMarker.BEEF, "\\b(?:rind\\w*|beef|manzo)\\b"),
        new AnimalTerm(MensaMarker.VEAL, "\\b(?:kalb\\w*|vitello)\\b"),
        new AnimalTerm(MensaMarker.LAMB, "\\b(?:lamm\\w*|schaf\\w*|agnello)\\b"),
        new AnimalTerm(MensaMarker.POULTRY, "\\b(?:huhn\\w*|hahnchen\\w*|haehnchen\\w*|pute\\w*|truthahn\\w*|geflugel\\w*|pollo|tacchino)\\b"),
        new AnimalTerm(MensaMarker.MEAT, "\\b(?:carne|ragu|fleisch\\w*)\\b")
    );

    public List<SaporiToscanaDish> parse(String html)
    {
        if (html == null || html.isBlank())
        {
            return List.of();
        }

        Document document = Jsoup.parse(html);
        List<SaporiToscanaDish> dishes = new ArrayList<>();
        for (Element heading : document.select("h1, h2, h3, h4, h5, h6"))
        {
            String day = weekday(heading.text());
            if (day == null)
            {
                continue;
            }

            Element block = heading.parent();
            while (block != null && block.select("p").isEmpty())
            {
                block = block.parent();
            }
            if (block == null)
            {
                continue;
            }

            List<Element> paragraphs = block.select("p");
            for (int i = 0; i < paragraphs.size(); i++)
            {
                Element paragraph = paragraphs.get(i);
                Element strong = paragraph.selectFirst("strong");
                if (strong == null || strong.text().isBlank())
                {
                    continue;
                }

                String name = strong.text().trim();
                BigDecimal price = null;
                String description = "";
                for (int j = i + 1; j < paragraphs.size(); j++)
                {
                    Element next = paragraphs.get(j);
                    if (next.selectFirst("strong") != null)
                    {
                        break;
                    }
                    String text = next.text().trim();
                    if (price == null)
                    {
                        price = parsePrice(text);
                    }
                    else if (!text.isBlank())
                    {
                        description = text;
                        break;
                    }
                }
                if (price != null)
                {
                    dishes.add(new SaporiToscanaDish(day, name, price, description, classify(name, description)));
                }
            }
        }
        return List.copyOf(dishes);
    }

    private static String weekday(String text)
    {
        if (text == null)
        {
            return null;
        }
        Matcher matcher = WEEKDAY.matcher(text.trim());
        return matcher.matches() ? matcher.group(1).toLowerCase(Locale.GERMAN) : null;
    }

    private static BigDecimal parsePrice(String text)
    {
        Matcher matcher = PRICE.matcher(text);
        if (!matcher.find())
        {
            return null;
        }
        String value = (matcher.group(1) != null ? matcher.group(1) : matcher.group(2)).replace(" ", "");
        try
        {
            return new BigDecimal(value.replace(',', '.'));
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private static List<MensaMarker> classify(String name, String description)
    {
        String source = normalize(name + " " + description);
        Set<MensaMarker> markers = EnumSet.noneOf(MensaMarker.class);
        for (AnimalTerm term : ANIMAL_TERMS)
        {
            if (Pattern.compile(term.regex()).matcher(source).find())
            {
                markers.add(term.marker());
            }
        }
        boolean containsAnimalMeatOrFish = markers.contains(MensaMarker.PORK)
            || markers.contains(MensaMarker.FISH)
            || markers.contains(MensaMarker.BEEF)
            || markers.contains(MensaMarker.LAMB)
            || markers.contains(MensaMarker.POULTRY)
            || markers.contains(MensaMarker.MEAT);
        if (!containsAnimalMeatOrFish && !description.isBlank())
        {
            markers.add(MensaMarker.VEGETARIAN);
        }
        return markers.stream().sorted().toList();
    }

    private static String normalize(String value)
    {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);
    }

    private record AnimalTerm(MensaMarker marker, String regex) {}

    public record SaporiToscanaDish(String weekday, String name, BigDecimal price,
                                   String description, List<MensaMarker> markers) {}
}
