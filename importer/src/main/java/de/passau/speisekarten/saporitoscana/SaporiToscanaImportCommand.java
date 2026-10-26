package de.passau.speisekarten.saporitoscana;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.passau.speisekarten.mensa.MensaMarker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Imports the current weekday-through-Friday lunch menu from i Sapori della Toscana. */
public final class SaporiToscanaImportCommand
{
    private static final String ID = "isapori";
    private static final String NAME = "i Sapori della Toscana";
    private static final String PAGE = "https://sapori-toscana.com/";
    private static final String ADDRESS = "Theresienstraße 23, 94032 Passau";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final SaporiToscanaMenuParser parser = new SaporiToscanaMenuParser();

    private SaporiToscanaImportCommand() {}

    public static void main(String[] args) throws IOException, InterruptedException
    {
        LocalDate referenceDate = args.length > 0 && !args[0].isBlank()
            ? LocalDate.parse(args[0])
            : LocalDate.now(ZONE);
        Path root = findRepositoryRoot(Path.of("").toAbsolutePath());
        new SaporiToscanaImportCommand().run(referenceDate,
            root.resolve("frontend/menus.json"), root.resolve("frontend/restaurants.json"));
    }

    void run(LocalDate referenceDate, Path menusPath, Path restaurantsPath) throws IOException, InterruptedException
    {
        if (referenceDate.getDayOfWeek() == DayOfWeek.SATURDAY
            || referenceDate.getDayOfWeek() == DayOfWeek.SUNDAY)
        {
            throw new IOException("I Sapori della Toscana lunch import expects a weekday; no files were changed.");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(PAGE))
            .header("User-Agent", "PassauSpeisekarten/1.0").GET().build();
        HttpResponse<String> response = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build()
            .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException("I Sapori della Toscana website returned HTTP " + response.statusCode());
        }

        List<SaporiToscanaMenuParser.SaporiToscanaDish> parsed = parser.parse(response.body());
        Map<String, LocalDate> datesByWeekday = new LinkedHashMap<>();
        for (LocalDate date = referenceDate; date.getDayOfWeek() != DayOfWeek.SATURDAY; date = date.plusDays(1))
        {
            datesByWeekday.put(dayName(date.getDayOfWeek()), date);
        }

        List<Map<String, Object>> imported = new ArrayList<>();
        for (Map.Entry<String, LocalDate> entry : datesByWeekday.entrySet())
        {
            List<SaporiToscanaMenuParser.SaporiToscanaDish> dayDishes = parsed.stream()
                .filter(dish -> dish.weekday().equals(entry.getKey()))
                .toList();
            if (dayDishes.isEmpty())
            {
                throw new IOException("No priced dishes found for " + entry.getKey()
                    + " in the i Sapori della Toscana page; existing JSON files left unchanged.");
            }

            List<Map<String, Object>> items = dayDishes.stream().map(SaporiToscanaImportCommand::toItem).toList();
            Map<String, Object> menu = new LinkedHashMap<>();
            menu.put("restaurantId", ID);
            menu.put("validFrom", entry.getValue().toString());
            menu.put("validUntil", entry.getValue().toString());
            menu.put("status", "CURRENT");
            menu.put("sourceUrl", PAGE);
            menu.put("items", items);
            imported.add(menu);
        }

        Map<String, Object> menusDocument = readDocument(menusPath);
        List<Map<String, Object>> menus = mapList(menusDocument.get("menus"));
        // Replace this source's import window, removing all historical entries and stale future entries.
        menus.removeIf(menu -> ID.equals(menu.get("restaurantId")));
        menus.addAll(imported);
        menus.sort(java.util.Comparator.comparing(menu -> String.valueOf(menu.get("validFrom"))));
        menusDocument.put("schemaVersion", 1);
        menusDocument.put("generatedAt", OffsetDateTime.now(ZONE).toString());
        menusDocument.put("menus", menus);

        Map<String, Object> restaurantsDocument = readDocument(restaurantsPath);
        List<Map<String, Object>> restaurants = mapList(restaurantsDocument.get("restaurants"));
        restaurants.removeIf(restaurant -> ID.equals(restaurant.get("id")));
        Map<String, Object> restaurant = new LinkedHashMap<>();
        restaurant.put("id", ID);
        restaurant.put("name", NAME);
        restaurant.put("address", ADDRESS);
        restaurant.put("sourceUrl", PAGE);
        restaurants.add(restaurant);
        restaurantsDocument.put("schemaVersion", 1);
        restaurantsDocument.put("restaurants", restaurants);

        writeAtomically(menusPath, menusDocument);
        writeAtomically(restaurantsPath, restaurantsDocument);
        int dishCount = imported.stream().mapToInt(menu -> ((List<?>) menu.get("items")).size()).sum();
        System.out.printf("I Sapori della Toscana importiert: %d Tage, %d Gerichte, Referenzdatum %s.%n",
            imported.size(), dishCount, referenceDate);
        imported.forEach(menu -> System.out.printf("  %s: %d Gerichte%n",
            menu.get("validFrom"), ((List<?>) menu.get("items")).size()));
    }

    private static Map<String, Object> toItem(SaporiToscanaMenuParser.SaporiToscanaDish dish)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", dish.name());
        item.put("category", "Hauptgerichte");
        item.put("price", dish.price());
        item.put("description", dish.description());
        item.put("dietary", dish.markers().stream().map(MensaMarker::name).sorted().toList());
        return item;
    }

    private static String dayName(DayOfWeek day)
    {
        return switch (day)
        {
            case MONDAY -> "montag";
            case TUESDAY -> "dienstag";
            case WEDNESDAY -> "mittwoch";
            case THURSDAY -> "donnerstag";
            case FRIDAY -> "freitag";
            default -> throw new IllegalArgumentException("Not a lunch-card weekday: " + day);
        };
    }

    private Map<String, Object> readDocument(Path path) throws IOException
    {
        if (!Files.exists(path))
        {
            throw new IOException("Required JSON file does not exist: " + path);
        }
        return mapper.readValue(path.toFile(), new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list)
        {
            for (Object item : list)
            {
                if (item instanceof Map<?, ?> map)
                {
                    result.add((Map<String, Object>) map);
                }
            }
        }
        return result;
    }

    private void writeAtomically(Path target, Object value) throws IOException
    {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "speisekarten-", ".json.tmp");
        try
        {
            mapper.writeValue(temporary.toFile(), value);
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
            if (Files.isDirectory(candidate.resolve("frontend")) && Files.isDirectory(candidate.resolve("importer")))
            {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Repository root not found; start from the repository or importer directory.");
    }
}
