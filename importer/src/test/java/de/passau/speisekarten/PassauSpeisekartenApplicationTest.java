package de.passau.speisekarten;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PassauSpeisekartenApplicationTest
{
    @Test
    void applicationEntryPointCanRun()
    {
        assertDoesNotThrow(() -> PassauSpeisekartenApplication.main(new String[0]));
    }
}
