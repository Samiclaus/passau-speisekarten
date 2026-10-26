package de.passau.speisekarten.mensa;

import java.util.regex.Pattern;

/** Normalizes the displayed dish name without trailing parenthesized allergens. */
public final class MensaDishNameNormalizer
{
    private static final Pattern TRAILING_PARENTHETICAL = Pattern.compile("\\s*\\([^()]*\\)\\s*$");

    private MensaDishNameNormalizer()
    {
    }

    public static String removeTrailingAllergenList(String dishName)
    {
        if (dishName == null)
        {
            return null;
        }

        return TRAILING_PARENTHETICAL.matcher(dishName).replaceFirst("").trim();
    }
}
