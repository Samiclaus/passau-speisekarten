package de.passau.speisekarten.mensa;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts the Studentenwerk CSV "kennz" value into normalized markers.
 *
 * <p>Markers are parsed as complete tokens. In particular, {@code V} must not
 * match {@code VG}. Unknown tokens are ignored so new source markers do not
 * accidentally acquire a meaning before they have been reviewed.</p>
 */
public final class MensaKennzeichenParser
{
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Map<String, MensaMarker> MARKERS = Map.ofEntries(
        Map.entry("V", MensaMarker.VEGETARIAN),
        Map.entry("VG", MensaMarker.VEGAN),
        Map.entry("MV", MensaMarker.MENSA_VITAL),
        Map.entry("G", MensaMarker.POULTRY),
        Map.entry("GF", MensaMarker.GLUTEN_FREE),
        Map.entry("R", MensaMarker.BEEF),
        Map.entry("B", MensaMarker.ORGANIC),
        Map.entry("S", MensaMarker.PORK),
        Map.entry("A", MensaMarker.CONTAINS_ALCOHOL),
        Map.entry("L", MensaMarker.LAMB),
        Map.entry("JD", MensaMarker.JURADISTL)
    );

    public Set<MensaMarker> parse(String kennzeichen)
    {
        if (kennzeichen == null || kennzeichen.isBlank())
        {
            return Set.of();
        }

        EnumSet<MensaMarker> result = EnumSet.noneOf(MensaMarker.class);
        for (String token : TOKEN_SEPARATOR.split(kennzeichen.toUpperCase(Locale.ROOT).trim()))
        {
            MensaMarker marker = MARKERS.get(token);
            if (marker != null)
            {
                result.add(marker);
            }
        }

        return result.isEmpty()
            ? Set.of()
            : Collections.unmodifiableSet(EnumSet.copyOf(result));
    }
}
