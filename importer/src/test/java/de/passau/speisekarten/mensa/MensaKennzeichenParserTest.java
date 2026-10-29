package de.passau.speisekarten.mensa;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MensaKennzeichenParserTest
{
    private final MensaKennzeichenParser parser = new MensaKennzeichenParser();

    @Test
    void parsesAllKnownMarkers()
    {
        assertEquals(
            Set.of(
                MensaMarker.VEGETARIAN,
                MensaMarker.VEGAN,
                MensaMarker.MENSA_VITAL,
                MensaMarker.POULTRY,
                MensaMarker.GLUTEN_FREE,
                MensaMarker.BEEF,
                MensaMarker.ORGANIC,
                MensaMarker.PORK,
                MensaMarker.CONTAINS_ALCOHOL,
                MensaMarker.LAMB,
                MensaMarker.JURADISTL
            ),
            parser.parse("V VG MV G GF R B S A L JD")
        );
    }

    @Test
    void veganMarkerIsPreservedWithoutAddingVegetarian()
    {
        Set<MensaMarker> markers = parser.parse("VG");

        assertTrue(markers.contains(MensaMarker.VEGAN));
        assertFalse(markers.contains(MensaMarker.VEGETARIAN));
        assertEquals(1, markers.size());
    }

    @Test
    void matchesCompleteTokensSoVDoesNotMatchVG()
    {
        assertEquals(
            Set.of(MensaMarker.VEGAN),
            parser.parse("VG")
        );
        assertEquals(
            Set.of(MensaMarker.VEGETARIAN),
            parser.parse("V")
        );
    }

    @Test
    void acceptsCommonSeparatorsAndIgnoresCase()
    {
        assertEquals(
            Set.of(MensaMarker.VEGAN, MensaMarker.GLUTEN_FREE),
            parser.parse("vg, GF")
        );
    }

    @Test
    void ignoresUnknownMarkersWithoutInventingMeaning()
    {
        assertEquals(Set.of(), parser.parse("XX"));
        assertEquals(Set.of(MensaMarker.VEGETARIAN), parser.parse("V XX"));
    }

    @Test
    void nullAndBlankValuesProduceNoMarkers()
    {
        assertEquals(Set.of(), parser.parse(null));
        assertEquals(Set.of(), parser.parse("  "));
    }
}
