package de.passau.speisekarten.mensa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MensaDishNameNormalizerTest
{
    @Test
    void removesTrailingParenthesizedAllergenList()
    {
        assertEquals(
            "Gemüsecurry mit Reis",
            MensaDishNameNormalizer.removeTrailingAllergenList("Gemüsecurry mit Reis (a, c, g)")
        );
    }

    @Test
    void leavesParenthesesInTheMiddleOfTheDishNameAlone()
    {
        assertEquals(
            "Pasta (Hausart) mit Gemüse",
            MensaDishNameNormalizer.removeTrailingAllergenList("Pasta (Hausart) mit Gemüse")
        );
    }

    @Test
    void trimsNameWithoutParentheses()
    {
        assertEquals(
            "Linseneintopf",
            MensaDishNameNormalizer.removeTrailingAllergenList("  Linseneintopf  ")
        );
    }

    @Test
    void handlesNull()
    {
        assertNull(MensaDishNameNormalizer.removeTrailingAllergenList(null));
    }
}
