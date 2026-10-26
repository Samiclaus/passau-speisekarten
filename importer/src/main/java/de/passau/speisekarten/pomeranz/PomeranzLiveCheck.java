package de.passau.speisekarten.pomeranz;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/** Live diagnostic: discovers the current lunch-card PDF and prints extracted metadata/text. */
public final class PomeranzLiveCheck {
    private PomeranzLiveCheck() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        var source = new PomeranzSource();
        URI pdfUri = source.findCurrentPdf(source.fetchPage(), PomeranzSource.MENU_PAGE);
        HttpRequest request = HttpRequest.newBuilder(pdfUri)
            .header("User-Agent", "PassauSpeisekarten/1.0")
            .GET().build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("PDF download failed with HTTP " + response.statusCode());
        String fileName = Path.of(pdfUri.getPath()).getFileName().toString();
        var menu = new PomeranzMenuParser().parse(new java.io.ByteArrayInputStream(response.body()), fileName);
        System.out.println("PDF: " + pdfUri);
        System.out.println("Zeitraum: " + menu.startDate() + " bis " + menu.endDate());
        System.out.println("Mittagsangebot: " + menu.serviceStart() + "–" + menu.serviceEnd());
        System.out.println("Kombipreis: " + menu.comboPrice());
        System.out.println("Vegetarisches Wochengericht: " + menu.vegetarianDish());
        System.out.println("--- PDF-Text zur Parserprüfung ---");
        System.out.println(menu.extractedText());
    }
}