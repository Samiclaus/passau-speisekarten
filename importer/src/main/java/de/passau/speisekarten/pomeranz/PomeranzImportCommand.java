package de.passau.speisekarten.pomeranz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.math.BigDecimal;
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

/** Imports Pomeranz's current lunch card and merges it with existing menus for other restaurants. */
public final class PomeranzImportCommand {
    private static final String RESTAURANT_ID = "pomeranz";
    private static final String RESTAURANT_NAME = "Restaurant Pomeranz";
    private static final String SOURCE_URL = "https://www.restaurant-pomeranz.de/Speisekarte/";
    private static final String ADDRESS = "Bratfischwinkel 5, 94032 Passau";
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private PomeranzImportCommand() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        Path root = findRepositoryRoot(Path.of("").toAbsolutePath());
        new PomeranzImportCommand().run(root.resolve("frontend/menus.json"), root.resolve("frontend/restaurants.json"));
    }

    void run(Path menusPath, Path restaurantsPath) throws IOException, InterruptedException {
        PomeranzSource source = new PomeranzSource();
        URI pdfUri = source.findCurrentPdf(source.fetchPage(), PomeranzSource.MENU_PAGE);
        HttpRequest request = HttpRequest.newBuilder(pdfUri).header("User-Agent", "PassauSpeisekarten/1.0").GET().build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Pomeranz PDF download failed with HTTP " + response.statusCode());

        String fileName = Path.of(pdfUri.getPath()).getFileName().toString();
        var menu = new PomeranzMenuParser().parse(new java.io.ByteArrayInputStream(response.body()), fileName);
        // Temporary test mode: intentionally accept historical PDF dates so the parser
        // can be checked against a real menu even after its service period has ended.
        if (menu.comboPrice() == null || menu.serviceStart() == null || menu.serviceEnd() == null)
            throw new IOException("Pomeranz PDF is missing price or service hours; refusing to publish");
        if (menu.vegetarianDish() == null || menu.vegetarianDish().isBlank()
            || menu.days().size() < 2
            || menu.days().stream().map(PomeranzMenuParser.PomeranzDay::date).distinct().count() != menu.days().size()
            || menu.days().stream().anyMatch(day -> day.dishText() == null || day.dishText().isBlank()))
            throw new IOException("Pomeranz PDF did not yield the weekly vegetarian dish and at least two complete weekday dishes; refusing to publish");

        List<Map<String, Object>> importedMenus = new ArrayList<>();
        for (var day : menu.days()) {
            List<Map<String, Object>> items = new ArrayList<>();
            if (day.vegetarianDish() != null) {
                items.add(toItem(day.vegetarianDish(), day.vegetarianDescription(), menu.comboPrice(),
                    "Hauptgerichte", List.of("VEGETARIAN")));
            }
            if (day.dishText() != null && !day.dishText().isBlank()) {
                items.add(toItem(day.dishText(), day.dishDescription(), menu.comboPrice(), "Hauptgerichte", List.of()));
            }
            if (items.isEmpty()) continue;
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("restaurantId", RESTAURANT_ID);
            record.put("validFrom", day.date().toString());
            record.put("validUntil", day.date().toString());
            record.put("status", "CURRENT");
            record.put("sourceUrl", pdfUri.toString());
            record.put("serviceStart", menu.serviceStart());
            record.put("serviceEnd", menu.serviceEnd());
            String components = menu.optionalComponents().isEmpty() ? "" :
                " Wahlweise dazu: " + String.join("; ", menu.optionalComponents()) + ".";
            record.put("offerNote", "Kombiangebot: " + menu.comboPrice().toPlainString().replace('.', ',')
                + " € für die Kombination der beiden Hauptgerichte." + components
                + " Nur in Verbindung mit mindestens einem Getränk.");
            record.put("items", items);
            importedMenus.add(record);
        }
        if (importedMenus.isEmpty()) throw new IOException("No publishable Pomeranz menus extracted; existing JSON files left unchanged");

        Map<String, Object> menusDocument = readDocument(menusPath);
        List<Map<String, Object>> allMenus = mapList(menusDocument.get("menus"));
        allMenus.removeIf(m -> RESTAURANT_ID.equals(m.get("restaurantId")));
        allMenus.addAll(importedMenus);
        allMenus.sort(java.util.Comparator.comparing(m -> String.valueOf(m.get("validFrom"))));
        menusDocument.put("schemaVersion", 1);
        menusDocument.put("generatedAt", OffsetDateTime.now(ZoneId.of("Europe/Berlin")).toString());
        menusDocument.put("menus", allMenus);

        Map<String, Object> restaurantsDocument = readDocument(restaurantsPath);
        List<Map<String, Object>> restaurants = mapList(restaurantsDocument.get("restaurants"));
        restaurants.removeIf(r -> RESTAURANT_ID.equals(r.get("id")));
        restaurants.add(pomeranzRestaurant(pdfUri));
        restaurantsDocument.put("schemaVersion", 1);
        restaurantsDocument.put("restaurants", restaurants);

        writeAtomically(menusPath, menusDocument);
        writeAtomically(restaurantsPath, restaurantsDocument);
        System.out.printf("Pomeranz importiert: %d Tage, %d Gerichte. Quelle: %s%n",
            importedMenus.size(), importedMenus.stream().mapToInt(m -> ((List<?>) m.get("items")).size()).sum(), pdfUri);
    }

    private static Map<String, Object> toItem(String name, String description, BigDecimal price,
                                                String category, List<String> dietary) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("category", category);
        item.put("price", price);
        item.put("description", description == null || description.isBlank() ? "" : description);
        item.put("dietary", dietary);
        return item;
    }

    private static Map<String, Object> pomeranzRestaurant(URI pdfUri) {
        Map<String, Object> restaurant = new LinkedHashMap<>();
        restaurant.put("id", RESTAURANT_ID);
        restaurant.put("name", RESTAURANT_NAME);
        restaurant.put("address", ADDRESS);
        restaurant.put("sourceUrl", SOURCE_URL);
        restaurant.put("openingHours", Map.of(
            "regularOpeningHours", regularOpeningHours(),
            "regularServiceHours", regularServiceHours(),
            "exceptions", List.of(),
            "sourceUrl", SOURCE_URL,
            "seasonalRuleNote", "Laut offizieller Website gelten an Feiertagen ebenfalls 11:30–14:00 und 17:30–23:00 Uhr. Sondertermine bitte anhand der Website prüfen."
        ));
        restaurant.put("menuPdfUrl", pdfUri.toString());
        return restaurant;
    }

    private static Map<String, Object> regularOpeningHours() {
        Map<String, Object> hours = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.getValue() >= DayOfWeek.TUESDAY.getValue() && day.getValue() <= DayOfWeek.SATURDAY.getValue()) {
                hours.put(day.name(), List.of(
                    Map.of("from", "11:30", "to", "14:00"),
                    Map.of("from", "17:30", "to", "23:00")));
            } else {
                hours.put(day.name(), List.of());
            }
        }
        return hours;
    }

    private static Map<String, Object> regularServiceHours() {
        Map<String, Object> hours = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            hours.put(day.name(),
                day.getValue() >= DayOfWeek.TUESDAY.getValue() && day.getValue() <= DayOfWeek.SATURDAY.getValue()
                    ? List.of(Map.of("from", "11:30", "to", "14:00"))
                    : List.of());
        }
        return hours;
    }

    private Map<String, Object> readDocument(Path path) throws IOException {
        if (!Files.exists(path)) throw new IOException("Required JSON file does not exist: " + path);
        return mapper.readValue(path.toFile(), new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> map) result.add((Map<String, Object>) map);
        }
        return result;
    }

    private void writeAtomically(Path target, Object value) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "speisekarten-", ".json.tmp");
        try {
            mapper.writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
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
