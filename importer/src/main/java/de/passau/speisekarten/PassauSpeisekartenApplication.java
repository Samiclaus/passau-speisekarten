package de.passau.speisekarten;

import de.passau.speisekarten.pomeranz.PomeranzImportCommand;
import de.passau.speisekarten.goldenesschiff.GoldenesSchiffImportCommand;
import de.passau.speisekarten.pomeranz.PomeranzLiveCheck;
import de.passau.speisekarten.saporitoscana.SaporiToscanaImportCommand;
import de.passau.speisekarten.wilmawunder.WilmaWunderImportCommand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Command-line entry point for the menu importer. */
public final class PassauSpeisekartenApplication
{
    private PassauSpeisekartenApplication()
    {
    }

    public static void main(String[] args) throws IOException, InterruptedException
    {
        if (args.length > 0 && "import-all".equals(args[0]))
        {
            System.out.println("Starte alle implementierten Speisekartenimporte.");
            List<String> failures = new ArrayList<>();

            runImport("1/5: Uni-Mensa Passau", () -> MensaHtmlImportCommand.main(new String[0]), failures);
            runImport("2/5: Pomeranz", () -> PomeranzImportCommand.main(new String[0]), failures);
            runImport("3/5: Goldenes Schiff", () -> GoldenesSchiffImportCommand.main(new String[0]), failures);
            runImport("4/5: I Sapori della Toscana", () -> SaporiToscanaImportCommand.main(new String[0]), failures);
            runImport("5/5: Wilma Wunder Passau", () -> WilmaWunderImportCommand.main(new String[0]), failures);

            if (failures.isEmpty())
            {
                System.out.println("Alle Speisekartenimporte abgeschlossen.");
            }
            else
            {
                System.out.printf(
                    "Importlauf abgeschlossen. Warnung: %d von 5 Quellen konnten nicht aktualisiert werden:%n",
                    failures.size()
                );
                failures.forEach(failure -> System.out.println(" - " + failure));
                System.out.println("Vorhandene Daten der fehlgeschlagenen Quellen bleiben erhalten, sofern der jeweilige Import atomar schreibt.");
            }
            return;
        }
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
        if (args.length > 0 && "import-goldenes-schiff".equals(args[0]))
        {
            GoldenesSchiffImportCommand.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && "import-sapori-toscana".equals(args[0]))
        {
            SaporiToscanaImportCommand.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && "import-wilma-wunder".equals(args[0]))
        {
            WilmaWunderImportCommand.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        System.out.println("Passau Speisekarten importer.");
        System.out.println("Alle implementierten Quellen importieren: import-all");
        System.out.println("Uni-Mensa: import-mensa-html [Startdatum YYYY-MM-DD] [Tage]");
        System.out.println("Pomeranz-PDF prüfen (schreibt keine Dateien): check-pomeranz");
        System.out.println("Pomeranz importieren und JSON ergänzen: import-pomeranz");
        System.out.println("Goldenes Schiff importieren und JSON ergänzen: import-goldenes-schiff");
        System.out.println("I Sapori della Toscana importieren: import-sapori-toscana [Referenzdatum YYYY-MM-DD]");
        System.out.println("Wilma Wunder importieren: import-wilma-wunder [Referenzdatum YYYY-MM-DD]");
    }

    private static void runImport(String label, ImportAction action, List<String> failures)
    {
        System.out.println(label);
        try
        {
            action.run();
        }
        catch (Exception exception)
        {
            String message = exception.getMessage();
            String failure = label + ": " + exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : " - " + message);
            failures.add(failure);
            System.err.println("[WARNUNG] " + failure);
        }
    }

    @FunctionalInterface
    private interface ImportAction
    {
        void run() throws Exception;
    }
}
