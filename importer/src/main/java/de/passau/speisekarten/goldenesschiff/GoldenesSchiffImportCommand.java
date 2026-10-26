package de.passau.speisekarten.goldenesschiff;

import de.passau.speisekarten.mensa.DishMarkerClassifier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

/** Imports the dated weekly PDF from the official Goldenes Schiff website. */
public final class GoldenesSchiffImportCommand {
    private static final String ID = "goldenes-schiff";
    private static final String NAME = "Wirtshaus & Pension Goldenes Schiff";
    private static final String PAGE = "https://goldenesschiff.de/";
    private static final String ADDRESS = "Unterer Sand 8, 94032 Passau";
    private static final BigDecimal DAY_PRICE = new BigDecimal("11.80");
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DishMarkerClassifier MARKER_CLASSIFIER = new DishMarkerClassifier();

    private GoldenesSchiffImportCommand() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        Path root = findRepositoryRoot(Path.of("").toAbsolutePath());
        new GoldenesSchiffImportCommand().run(root.resolve("frontend/menus.json"), root.resolve("frontend/restaurants.json"));
    }

    void run(Path menusPath, Path restaurantsPath) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest pageRequest = HttpRequest.newBuilder(URI.create(PAGE))
            .header("User-Agent", "PassauSpeisekarten/1.0").GET().build();
        HttpResponse<String> pageResponse = client.send(pageRequest, HttpResponse.BodyHandlers.ofString());
        if (pageResponse.statusCode() < 200 || pageResponse.statusCode() >= 300)
            throw new IOException("Goldenes Schiff website returned HTTP " + pageResponse.statusCode());
        Document page = Jsoup.parse(pageResponse.body(), PAGE);
        Element weeklyLink = page.select("a").stream()
            .filter(a -> a.text().toLowerCase(java.util.Locale.GERMAN).contains("wochenkarte"))
            .findFirst().orElseThrow(() -> new IOException("No Wochenkarte link found on Goldenes Schiff website"));
        URI pdfUri = URI.create(weeklyLink.absUrl("href"));
        HttpRequest pdfRequest = HttpRequest.newBuilder(pdfUri).header("User-Agent", "PassauSpeisekarten/1.0").GET().build();
        HttpResponse<byte[]> pdfResponse = client.send(pdfRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (pdfResponse.statusCode() < 200 || pdfResponse.statusCode() >= 300)
            throw new IOException("Goldenes Schiff weekly PDF returned HTTP " + pdfResponse.statusCode());

        List<GoldenesSchiffMenuParser.DayMenu> days = new GoldenesSchiffMenuParser()
            .parse(new java.io.ByteArrayInputStream(pdfResponse.body()), Path.of(pdfUri.getPath()).getFileName().toString(),
                LocalDate.now(ZoneId.of("Europe/Berlin")));
        if (days.isEmpty() || days.stream().anyMatch(d -> d.mainDish().isBlank() || d.vegetarianDish().isBlank()))
            throw new IOException("Goldenes Schiff PDF yielded no complete main/vegetarian dish pairs; existing JSON files left unchanged");

        List<Map<String, Object>> imported = new ArrayList<>();
        for (var day : days) {
            List<Map<String, Object>> items = new ArrayList<>();
            items.add(item(day.mainDish(), "Hauptgerichte", DAY_PRICE, List.of()));
            items.add(item(day.vegetarianDish(), "Hauptgerichte", DAY_PRICE, List.of("VEGETARIAN")));
            Map<String, Object> menu = new LinkedHashMap<>();
            menu.put("restaurantId", ID);
            menu.put("validFrom", day.date().toString());
            menu.put("validUntil", day.date().toString());
            menu.put("status", "CURRENT");
            menu.put("sourceUrl", pdfUri.toString());
            menu.put("items", items);
            imported.add(menu);
        }

        Map<String, Object> menusDoc = readDocument(menusPath);
        List<Map<String, Object>> menus = mapList(menusDoc.get("menus"));
        menus.removeIf(m -> ID.equals(m.get("restaurantId")));
        menus.addAll(imported);
        menus.sort(java.util.Comparator.comparing(m -> String.valueOf(m.get("validFrom"))));
        menusDoc.put("schemaVersion", 1);
        menusDoc.put("generatedAt", OffsetDateTime.now(ZoneId.of("Europe/Berlin")).toString());
        menusDoc.put("menus", menus);

        Map<String, Object> restaurantsDoc = readDocument(restaurantsPath);
        List<Map<String, Object>> restaurants = mapList(restaurantsDoc.get("restaurants"));
        restaurants.removeIf(r -> ID.equals(r.get("id")));
        Map<String, Object> restaurant = new LinkedHashMap<>();
        restaurant.put("id", ID);
        restaurant.put("name", NAME);
        restaurant.put("address", ADDRESS);
        restaurant.put("sourceUrl", PAGE);
        restaurant.put("menuPdfUrl", pdfUri.toString());
        restaurants.add(restaurant);
        restaurantsDoc.put("schemaVersion", 1);
        restaurantsDoc.put("restaurants", restaurants);

        writeAtomically(menusPath, menusDoc);
        writeAtomically(restaurantsPath, restaurantsDoc);
        System.out.printf("Goldenes Schiff importiert: %d Tage, %d Gerichte. PDF: %s%n",
            imported.size(), imported.size() * 2, pdfUri);
    }

    private static Map<String, Object> item(String name, String category, BigDecimal price, List<String> dietary) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("category", category);
        item.put("price", price);
        List<String> markers = new ArrayList<>(dietary);
        MARKER_CLASSIFIER.classify(name, "").stream()
            .map(Enum::name)
            .filter(marker -> !markers.contains(marker))
            .forEach(markers::add);
        item.put("dietary", markers.stream().sorted().toList());
        return item;
    }

    private Map<String, Object> readDocument(Path path) throws IOException {
        if (!Files.exists(path)) throw new IOException("Required JSON file does not exist: " + path);
        return mapper.readValue(path.toFile(), new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list)
            for (Object item : list) if (item instanceof Map<?, ?> map) result.add((Map<String, Object>) map);
        return result;
    }

    private void writeAtomically(Path target, Object value) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "speisekarten-", ".json.tmp");
        try {
            mapper.writeValue(temporary.toFile(), value);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Path findRepositoryRoot(Path current) {
        Path candidate = current;
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("frontend")) && Files.isDirectory(candidate.resolve("importer"))) return candidate;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate repository root (expected frontend/ and importer/)");
    }
}
