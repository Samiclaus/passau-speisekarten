package de.passau.speisekarten.mensa;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MensaHtmlPostClientTest
{
    @Test
    void buildsExpectedFormParametersForPassau()
    {
        assertEquals(
            Map.of(
                "func", "make_spl",
                "locId", "UNI-P",
                "lang", "de",
                "date", "2026-10-26",
                "w", ""
            ),
            MensaHtmlPostClient.buildFormParameters(
                LocalDate.of(2026, 10, 26),
                "UNI-P"
            )
        );
    }

    @Test
    void locationIdIsConfigurable()
    {
        assertEquals(
            "UNI-X",
            MensaHtmlPostClient.buildFormParameters(
                LocalDate.of(2026, 10, 26),
                "UNI-X"
            ).get("locId")
        );
    }
}
