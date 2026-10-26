package de.passau.speisekarten.mensa;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared heuristic classifier for dish names and descriptions across restaurant importers.
 *
 * <p>Only recognized words produce animal markers. Vegetarian is inferred from a non-empty
 * description only when a caller explicitly opts into that source-specific heuristic; absence
 * of a recognized animal word is not proof that a dish is vegetarian.</p>
 */
public final class DishMarkerClassifier
{
    private static final List<AnimalTerm> ANIMAL_TERMS = List.of(
        new AnimalTerm(MensaMarker.PORK, "\\b(?:nduja|salsiccia|salsicia|speck|maiale|porco|schwein\\w*|pancetta|prosciutto|salami|guanciale|pork)\\b"),
        new AnimalTerm(MensaMarker.FISH, "\\b(?:salmone|salmon|lachs\\w*|tonno|thunfisch\\w*|fisch\\w*|pesce|sardellen|anchovis|anchovy|fish)\\b"),
        new AnimalTerm(MensaMarker.BEEF, "\\b(?:rind\\w*|beef|manzo)\\b"),
        new AnimalTerm(MensaMarker.VEAL, "\\b(?:kalb\\w*|vitello|veal)\\b"),
        new AnimalTerm(MensaMarker.LAMB, "\\b(?:lamm\\w*|schaf\\w*|agnello|lamb|mutton)\\b"),
        new AnimalTerm(MensaMarker.POULTRY, "\\b(?:huhn\\w*|hahnchen\\w*|haehnchen\\w*|pute\\w*|truthahn\\w*|geflugel\\w*|pollo|tacchino|chicken|turkey|poultry)\\b"),
        new AnimalTerm(MensaMarker.GAME_MEAT, "\\b(?:wildfleisch\\w*|wildschwein\\w*|hirsch\\w*|reh\\w*|venison|game meat)\\b"),
        new AnimalTerm(MensaMarker.MEAT, "\\b(?:carne|ragu|fleisch\\w*|meat)\\b")
    );

    private static final Pattern EXPLICIT_VEGAN = Pattern.compile("\\b(?:vegan|vegano|vegana)\\b");
    private static final Pattern EXPLICIT_VEGETARIAN = Pattern.compile("\\b(?:vegetarisch\\w*|vegetarian|vegetariano|vegetariana)\\b");

    public List<MensaMarker> classify(String name, String description)
    {
        return classify(name, description, false);
    }

    public List<MensaMarker> classify(String name, String description, boolean inferVegetarianFromDescription)
    {
        String source = normalize((name == null ? "" : name) + " " + (description == null ? "" : description));
        Set<MensaMarker> markers = EnumSet.noneOf(MensaMarker.class);

        for (AnimalTerm term : ANIMAL_TERMS)
        {
            if (term.pattern().matcher(source).find())
            {
                markers.add(term.marker());
            }
        }

        if (EXPLICIT_VEGAN.matcher(source).find())
        {
            markers.add(MensaMarker.VEGAN);
            markers.add(MensaMarker.VEGETARIAN);
        }
        else if (EXPLICIT_VEGETARIAN.matcher(source).find())
        {
            markers.add(MensaMarker.VEGETARIAN);
        }

        boolean hasAnimalMarker = markers.stream().anyMatch(DishMarkerClassifier::isAnimalMarker);
        if (!hasAnimalMarker && inferVegetarianFromDescription && description != null && !description.isBlank()
            && !markers.contains(MensaMarker.VEGAN) && !markers.contains(MensaMarker.VEGETARIAN))
        {
            markers.add(MensaMarker.VEGETARIAN);
        }

        return markers.stream().sorted().toList();
    }

    private static boolean isAnimalMarker(MensaMarker marker)
    {
        return switch (marker)
        {
            case PORK, FISH, BEEF, VEAL, LAMB, POULTRY, GAME_MEAT, MEAT -> true;
            default -> false;
        };
    }

    private static String normalize(String value)
    {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);
    }

    private record AnimalTerm(MensaMarker marker, Pattern pattern)
    {
        private AnimalTerm(MensaMarker marker, String regex)
        {
            this(marker, Pattern.compile(regex));
        }
    }
}
