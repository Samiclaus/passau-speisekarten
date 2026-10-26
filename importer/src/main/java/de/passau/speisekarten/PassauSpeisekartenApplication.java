package de.passau.speisekarten;

import java.io.IOException;
import java.util.Arrays;

/** Command-line entry point for the menu importer. */
public final class PassauSpeisekartenApplication
{
    private PassauSpeisekartenApplication()
    {
    }

    public static void main(String[] args) throws IOException
    {
        if (args.length > 0 && "import-mensa-html".equals(args[0]))
        {
            MensaHtmlImportCommand.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        System.out.println("Passau Speisekarten importer.");
        System.out.println("Zum Import der Uni-Mensa: import-mensa-html [Startdatum YYYY-MM-DD] [Tage]");
    }
}
