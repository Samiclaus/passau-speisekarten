package de.passau.speisekarten.mensa;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Retrieves the rendered STWNO menu for a date and location.
 *
 * <p>This is a transport client only. The returned document is not yet
 * interpreted as dishes; that is deliberately kept in a separate parser.</p>
 */
public final class MensaHtmlPostClient
{
    public static final String DEFAULT_ENDPOINT =
        "https://www.stwno.de/infomax/daten-extern/html/speiseplan-render.php";

    private static final String REFERER =
        "https://www.stwno.de/infomax/daten-extern/html/speiseplaene.php?einrichtung=%s&w=dw";

    private static final DateTimeFormatter REQUEST_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String endpoint;
    private final String locationId;

    public MensaHtmlPostClient()
    {
        this(DEFAULT_ENDPOINT, "UNI-P");
    }

    public MensaHtmlPostClient(String endpoint, String locationId)
    {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.locationId = Objects.requireNonNull(locationId, "locationId");
    }

    public Document fetch(LocalDate date) throws IOException
    {
        Objects.requireNonNull(date, "date");

        Connection.Response response = Jsoup.connect(endpoint)
            .method(Connection.Method.POST)
            .data(buildFormParameters(date, locationId))
            .header("Accept", "application/json")
            .header("X-Request", "JSON")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", "https://www.stwno.de")
            .header("Referer", REFERER.formatted(locationId))
            .ignoreContentType(true)
            .timeout(15_000)
            .execute();

        return Jsoup.parse(response.body(), endpoint);
    }

    static Map<String, String> buildFormParameters(LocalDate date, String locationId)
    {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(locationId, "locationId");

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("func", "make_spl");
        parameters.put("locId", locationId);
        parameters.put("lang", "de");
        parameters.put("date", REQUEST_DATE.format(date));
        parameters.put("w", "dw");
        return Map.copyOf(parameters);
    }
}
