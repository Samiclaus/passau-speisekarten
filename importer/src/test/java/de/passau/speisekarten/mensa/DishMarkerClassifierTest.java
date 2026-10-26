package de.passau.speisekarten.mensa;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DishMarkerClassifierTest
{
    private final DishMarkerClassifier classifier = new DishMarkerClassifier();

    @Test
    void recognizesAnimalTermsAcrossGermanAndItalianDishNames()
    {
        assertTrue(classifier.classify("Spaghettoni Nduja", "").contains(MensaMarker.PORK));
        assertTrue(classifier.classify("Farfale con panna e salmone", "").contains(MensaMarker.FISH));
        assertTrue(classifier.classify("Penne al Ragù", "").contains(MensaMarker.MEAT));
        assertTrue(classifier.classify("Kalbsschnitzel", "").contains(MensaMarker.VEAL));
        assertTrue(classifier.classify("Pollo al forno", "").contains(MensaMarker.POULTRY));
        assertTrue(classifier.classify("Wildschweinragout", "").contains(MensaMarker.GAME_MEAT));
    }

    @Test
    void doesNotInferVegetarianFromNameAloneOrFromNoKnownAnimalTerm()
    {
        assertEquals(List.of(), classifier.classify("Pasta al pomodoro", ""));
        assertEquals(List.of(), classifier.classify("Pasta al pomodoro", "Tomaten, Basilikum"));
    }

    @Test
    void vegetarianInferenceIsAnExplicitOptInForSourcesWithDescriptions()
    {
        assertTrue(classifier.classify("Pasta al pomodoro", "Tomaten, Basilikum", true)
            .contains(MensaMarker.VEGETARIAN));
        assertFalse(classifier.classify("Pasta al Ragù", "Tomaten", true)
            .contains(MensaMarker.VEGETARIAN));
    }

    @Test
    void explicitVeganAlsoMeansVegetarian()
    {
        List<MensaMarker> markers = classifier.classify("Pizza vegan", "");
        assertTrue(markers.contains(MensaMarker.VEGAN));
        assertTrue(markers.contains(MensaMarker.VEGETARIAN));
    }
}
