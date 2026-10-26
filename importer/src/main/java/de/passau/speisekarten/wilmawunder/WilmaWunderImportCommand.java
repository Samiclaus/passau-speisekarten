package de.passau.speisekarten.wilmawunder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.passau.speisekarten.mensa.DishMarkerClassifier;
import de.passau.speisekarten.mensa.MensaMarker;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Imports the current weekly lunch offer from Wilma Wunder Passau's AmadeusGo menu. */
public final class WilmaWunderImportCommand
{
    private static final String ID = "wilma-wunder";
    private static final String NAME = "Wilma Wunder Passau";
    private static final String PAGE = "https://www.wilma-wunder.de/restaurants/passau/";
    private static final String ADDRESS = "Ludwigstraße 8, 94032 Passau";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final WilmaWunderMenuParser parser = new WilmaWunderMenuParser();
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private WilmaWunderImportCommand() {}

    public static void main(String[] args) throws IOException, InterruptedException
    {
        LocalDate referenceDate = args.length > 0 && !args[0].isBlank()
            ? LocalDate.parse(args[0])
            : LocalDate.now(ZONE);
        Path root = findRepositoryRoot(Path.of("").toAbsolutePath());
        new WilmaWunderImportCommand().run(referenceDate,
            root.resolve("frontend/menus.json"), root.resolve("frontend/restaurants.json"));
    }

    void run(LocalDate referenceDate, Path menusPath, Path restaurantsPath) throws IOException, InterruptedException
    {
        if (referenceDate.getDayOfWeek() == DayOfWeek.SATURDAY
            || referenceDate.getDayOfWeek() == DayOfWeek.SUNDAY)
        {
            throw new IOException("Wilma Wunder import expects a weekday; no files were changed.");
        }

        HttpResponse<String> landing = get(PAGE);
        Document landingDocument = Jsoup.parse(landing.body(), landing.uri().toString());
        URI menuUri = findAmadeusGoMenu(landingDocument);
        HttpResponse<String> menuResponse = get(menuUri.toString());

        List<WilmaWunderMenuParser.Dish> dishes;
        try
        {
            dishes = parser.parse(menuResponse.body());
        }
        catch (IllegalArgumentException exception)
        {
            throw new IOException("Could not parse Wilma Wunder's AmadeusGo lunch menu: "
                + exception.getMessage() + "; existing JSON files left unchanged.", exception);
        }

        List<Map<String, Object>> imported = new ArrayList<>();
        for (LocalDate date = referenceDate; date.getDayOfWeek() != DayOfWeek.SATURDAY; date = date.plusDays(1))
        {
            if (isBavarianPublicHoliday(date))
            {
                System.out.printf("Skipping public holiday %s.%n", date);
                continue;
            }

            List<Map<String, Object>> items = dishes.stream().map(WilmaWunderImportCommand::toItem).toList();
            Map<String, Object> menu = new LinkedHashMap<>();
            menu.put("restaurantId", ID);
            menu.put("validFrom", date.toString());
            menu.put("validUntil", date.toString());
            menu.put("status", "CURRENT");
            menu.put("sourceUrl", menuUri.toString());
            menu.put("items", items);
            imported.add(menu);
        }

        if (imported.isEmpty())
        {
            throw new IOException("No non-holiday weekdays remain for Wilma Wunder; existing JSON files left unchanged.");
        }

        Map<String, Object> menusDocument = readDocument(menusPath);
        List<Map<String, Object>> menus = mapList(menusDocument.get("menus"));
        // Replace this source completely on every run: this removes past entries as well as stale future ones.
        long removedMenus = menus.stream().filter(menu -> ID.equals(menu.get("restaurantId"))).count();
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
        System.out.printf("Wilma Wunder importiert: %d Tage, %d Gerichte, Referenzdatum %s; %d alte Einträge ersetzt.%n",
            imported.size(), dishCount, referenceDate, removedMenus);
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "PassauSpeisekarten/1.0").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException("Wilma Wunder menu request returned HTTP " + response.statusCode() + " for " + url);
        }
        return response;
    }

    private URI findAmadeusGoMenu(Document document) throws IOException, InterruptedException
    {
        List<Element> candidates = document.select("a[href]").stream()
            .filter(anchor -> {
                String text = anchor.text().toLowerCase(Locale.ROOT);
                String href = anchor.absUrl("href").toLowerCase(Locale.ROOT);
                return text.contains("karte") || text.contains("speisekarte")
                    || href.contains("amadeusgo") || href.contains("amadeus-go");
            })
            .toList();

        for (Element candidate : candidates)
        {
            String href = candidate.absUrl("href");
            if (href.isBlank())
            {
                continue;
            }
            try
            {
                HttpResponse<String> response = get(href);
                String host = response.uri().getHost();
                if (host != null && host.toLowerCase(Locale.ROOT).contains("amadeusgo"))
                {
                    return response.uri();
                }

                // Some redirect endpoints return the menu HTML directly on their final host.
                if (response.body().contains("articleitem_price")
                    && response.body().contains("Mittagsangebot"))
                {
                    return response.uri();
                }
            }
            catch (IOException exception)
            {
                System.err.println("Could not follow candidate menu link " + href + ": " + exception.getMessage());
            }
        }
        throw new IOException("No usable AmadeusGo menu link found on " + PAGE
            + "; existing JSON files left unchanged.");
    }

    private static Map<String, Object> toItem(WilmaWunderMenuParser.Dish dish)
    {
        List<MensaMarker> markers = new ArrayList<>(new DishMarkerClassifier().classify(dish.name(), ""));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", dish.name());
        item.put("category", "Hauptgerichte");
        item.put("price", dish.price());
        item.put("dietary", markers.stream().distinct().map(MensaMarker::name).sorted().toList());
        return item;
    }

    private static boolean isAnimalMarker(MensaMarker marker)
    {
        return switch (marker)
        {
            case POULTRY, BEEF, VEAL, PORK, LAMB, GAME_MEAT, FISH, MEAT -> true;
            default -> false;
        };
    }

    private static boolean isBavarianPublicHoliday(LocalDate date)
    {
        int year = date.getYear();
        LocalDate easter = easterSunday(year);
        List<LocalDate> holidays = List.of(
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 1, 6),
            easter.minusDays(2),
            easter.plusDays(1),
            LocalDate.of(year, 5, 1),
            easter.plusDays(39),
            easter.plusDays(50),
            LocalDate.of(year, 8, 15),
            LocalDate.of(year, 10, 3),
            LocalDate.of(year, 11, 1),
            LocalDate.of(year, 12, 25),
            LocalDate.of(year, 12, 26)
        );
        return holidays.contains(date);
    }

    private static LocalDate easterSunday(int year)
    {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
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
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored)
            {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
