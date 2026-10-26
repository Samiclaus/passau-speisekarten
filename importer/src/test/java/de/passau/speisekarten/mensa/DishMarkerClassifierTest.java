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
    void infersVegetarianWhenNoAnimalMarkerIsRecognized()
    {
        assertTrue(classifier.classify("Pasta al pomodoro", "").contains(MensaMarker.VEGETARIAN));
        assertTrue(classifier.classify("Pasta al pomodoro", "Tomaten, Basilikum").contains(MensaMarker.VEGETARIAN));
        assertFalse(classifier.classify("Pasta al Ragù", "Tomaten").contains(MensaMarker.VEGETARIAN));
        assertTrue(classifier.classify("Pasta mit Hackfleisch", "").contains(MensaMarker.MEAT));
        assertFalse(classifier.classify("Pasta mit Hackfleisch", "").contains(MensaMarker.VEGETARIAN));
        assertTrue(classifier.classify("Brot mit Schinken", "").contains(MensaMarker.PORK));
        assertFalse(classifier.classify("Brot mit Schinken", "").contains(MensaMarker.VEGETARIAN));
    }

    @Test
    void explicitVeganAlsoMeansVegetarian()
    {
        List<MensaMarker> markers = classifier.classify("Pizza vegan", "");
        assertTrue(markers.contains(MensaMarker.VEGAN));
        assertTrue(markers.contains(MensaMarker.VEGETARIAN));
    }
}
