package de.passau.speisekarten;

import de.passau.speisekarten.pomeranz.PomeranzImportCommand;
import de.passau.speisekarten.pomeranz.PomeranzLiveCheck;
import java.io.IOException;
import java.util.Arrays;

/** Command-line entry point for the menu importer. */
public final class PassauSpeisekartenApplication
{
    private PassauSpeisekartenApplication()
    {
    }

    public static void main(String[] args) throws IOException, InterruptedException
    {
        if (args.length > 0 && "import-mensa-html".equals(args[0]))
        {
            MensaHtmlImportCommand.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && "check-pomeranz".equals(args[0]))
        {
            PomeranzLiveCheck.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && "import-pomeranz".equals(args[0]))
        {
            PomeranzImportCommand.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        System.out.println("Passau Speisekarten importer.");
        System.out.println("Uni-Mensa: import-mensa-html [Startdatum YYYY-MM-DD] [Tage]");
        System.out.println("Pomeranz-PDF prüfen (schreibt keine Dateien): check-pomeranz");
        System.out.println("Pomeranz importieren und JSON ergänzen: import-pomeranz");
    }
}
