package de.passau.speisekarten;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.passau.speisekarten.mensa.MensaHtmlDish;
import de.passau.speisekarten.mensa.MensaHtmlPostClient;
import de.passau.speisekarten.mensa.MensaHtmlResponseParser;
import de.passau.speisekarten.mensa.MensaMarker;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports the Passau university cafeteria's HTML menu and writes the JSON files
 * consumed by the static frontend. Output files are only replaced after every
 * requested date has been fetched successfully.
 */
public final class MensaHtmlImportCommand
{
    private static final String RESTAURANT_ID = "uni-passau-mensa";
    private static final String RESTAURANT_NAME = "Uni-Mensa Passau";
    private static final String SOURCE_URL =
        "https://www.stwno.de/infomax/daten-extern/html/speiseplaene.php?einrichtung=UNI-P";
    private static final int DEFAULT_DAYS = 7;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    private final MensaHtmlPostClient client = new MensaHtmlPostClient();
    private final MensaHtmlResponseParser parser = new MensaHtmlResponseParser();

    public static void main(String[] args) throws IOException
    {
        LocalDate startDate = args.length > 0 && !args[0].isBlank()
            ? LocalDate.parse(args[0])
            : LocalDate.now(ZoneId.of("Europe/Berlin"));
        int days = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_DAYS;
        if (days < 1 || days > 31)
        {
            throw new IllegalArgumentException("Die Anzahl der Tage muss zwischen 1 und 31 liegen.");
        }

        Path repositoryRoot = findRepositoryRoot(Path.of("").toAbsolutePath());
        Path menusPath = repositoryRoot.resolve("frontend/menus.json");
        Path restaurantsPath = repositoryRoot.resolve("frontend/restaurants.json");
        new MensaHtmlImportCommand().run(startDate, days, menusPath, restaurantsPath);
    }

    void run(LocalDate startDate, int days, Path menusPath, Path restaurantsPath) throws IOException
    {
        List<Map<String, Object>> menus = new ArrayList<>();
        int totalDishes = 0;

        for (int offset = 0; offset < days; offset++)
        {
            LocalDate date = startDate.plusDays(offset);
            Document response = client.fetch(date);
            List<MensaHtmlDish> dishes = parser.parse(response.html());
            List<Map<String, Object>> items = dishes.stream()
                .map(MensaHtmlImportCommand::toItem)
                .toList();

            Map<String, Object> menu = new LinkedHashMap<>();
            menu.put("restaurantId", RESTAURANT_ID);
            menu.put("validFrom", date.toString());
            menu.put("validUntil", date.toString());
            menu.put("status", "CURRENT");
            menu.put("sourceUrl", SOURCE_URL);
            menu.put("items", items);
            menus.add(menu);
            totalDishes += dishes.size();
            System.out.printf("%s: %d Gerichte importiert.%n", date, dishes.size());
        }

        Map<String, Object> menusDocument = new LinkedHashMap<>();
        menusDocument.put("schemaVersion", 1);
        menusDocument.put("generatedAt", OffsetDateTime.now(ZoneId.of("Europe/Berlin")).toString());
        menusDocument.put("menus", menus);

        Map<String, Object> restaurant = new LinkedHashMap<>();
        restaurant.put("id", RESTAURANT_ID);
        restaurant.put("name", RESTAURANT_NAME);
        restaurant.put("address", "Innstraße 27, 94032 Passau");
        restaurant.put("sourceUrl", SOURCE_URL);

        Map<String, Object> restaurantsDocument = new LinkedHashMap<>();
        restaurantsDocument.put("schemaVersion", 1);
        restaurantsDocument.put("restaurants", List.of(restaurant));

        writeAtomically(menusPath, menusDocument);
        writeAtomically(restaurantsPath, restaurantsDocument);
        System.out.printf(
            "Import abgeschlossen: %d Tage, %d Gerichte.%nMenüs: %s%nLokale: %s%n",
            days, totalDishes, menusPath, restaurantsPath
        );
    }

    private static Map<String, Object> toItem(MensaHtmlDish dish)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", dish.name());
        item.put("category", dish.category());
        item.put("price", dish.studentPrice());
        item.put("studentPrice", dish.studentPrice());
        item.put("employeePrice", dish.employeePrice());
        item.put("guestPrice", dish.guestPrice());
        item.put("dietary", dish.markers().stream().map(MensaMarker::name).sorted().toList());
        return item;
    }

    private void writeAtomically(Path target, Object value) throws IOException
    {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "speisekarten-", ".json.tmp");
        try
        {
            objectMapper.writeValue(temporary.toFile(), value);
            try
            {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored)
            {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path findRepositoryRoot(Path current)
    {
        Path candidate = current;
        while (candidate != null)
        {
            if (Files.isDirectory(candidate.resolve("frontend"))
                && Files.isDirectory(candidate.resolve("importer")))
            {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
            "Repository-Wurzel nicht gefunden. Bitte aus dem Repository oder dem importer-Verzeichnis starten."
        );
    }
}
