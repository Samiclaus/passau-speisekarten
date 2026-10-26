# Passauer Speisekarten

Statischer Aggregator für Mittags-, Tages- und Wochenkarten in Passau.

## Architektur
- `importer/`: Java 21 / Maven für Abruf, Normalisierung und JSON-Export.
- `config/sources.csv`: konfigurierbare Kartenquellen.
- `corrections/menu-decisions.json`: persistente manuelle Korrekturen.
- `frontend/`: statische Webseite und veröffentlichte JSON-Dateien für Cloudflare Pages.
- `.github/workflows/`: CI und geplanter Import.

Die erste Version benötigt keine Datenbank. Git hält Änderungen an Konfiguration und Daten nachvollziehbar fest.

## Erste Quellenkandidaten
- **Uni-Mensa Passau (CSV, Alternative A):** `https://www.stwno.de/infomax/daten-extern/csv/UNI-P/{isoWeek}.csv`; `{isoWeek}` ist der Abrufparameter. Ein beobachteter Versatz zwischen Dateinummer und enthaltenem Wochenplan kann ein temporärer Fehler sein und wird nicht als allgemeine Regel vorausgesetzt. Der Adapter soll anhand der tatsächlichen Datums-/Tagesangaben prüfen, welche Woche enthalten ist; die Spalte `kennz` liefert strukturierte Kennzeichen.
- **Uni-Mensa Passau (HTML-POST, Alternative B):** Endpoint `https://www.stwno.de/infomax/daten-extern/html/speiseplan-render.php`. Bekannte Formularparameter: `func=make_spl`, `locId=UNI-P`, `lang=de`, `date=yyyy-MM-dd`, `w=` (leer, wie im beobachteten Browserrequest). Der Request verwendet `application/x-www-form-urlencoded`, `X-Requested-With: XMLHttpRequest` und den Referer `https://www.stwno.de/infomax/daten-extern/html/speiseplaene.php?einrichtung=UNI-P`. Die Antwort ist ein HTML-Fragment mit `tr.gruppenkopf` für Kategorien und `tr.essenzeile` für Gerichte. Namen stehen in `td.cell1`, Kennzeichen in `td.cell2` als Icon-`alt`/Tooltip-Text und Preise in den drei `td.preis`-Zellen (Studierende, Beschäftigte, Gäste). `MensaHtmlResponseParser` extrahiert diese Informationen; Allergene in `sup`-Elementen werden aus dem angezeigten Namen entfernt.

Die beiden Mensa-Varianten sind bewusst als getrennte, deaktivierte Quellen konfiguriert. Wir vergleichen Zuverlässigkeit, Datenvollständigkeit und Wartbarkeit, bevor wir eine davon aktivieren; eine automatische Vermischung beider Quellen ist zunächst nicht vorgesehen.
- **Restaurant Pomeranz (PDF):** die offizielle Seite `https://www.restaurant-pomeranz.de/Speisekarte/` enthält den aktuellen PDF-Link. Nicht die wechselnde PDF-URL fest konfigurieren, sondern auf der Seite den Link zur Mittagskarte ermitteln.
- **I Sapori della Toscana (HTML, bevorzugter Kandidat):** `https://sapori-toscana.com/` zeigt Mittagsgerichte nach Wochentag samt Preisen. Das eignet sich gut, um einen HTML-Adapter für wiederkehrende Tagesabschnitte zu testen.
- **Wirtshaus & Pension Goldenes Schiff (HTML, Alternative):** `https://goldenesschiff.de/` zeigt einen aktuellen Mittagstisch und eine Wochenkarte. Das ist eine zweite Option, falls sich die Daten bei I Sapori nicht stabil extrahieren lassen.

Die Einträge in `config/sources.csv` sind zunächst deaktiviert. Erst nach Implementierung und Tests des jeweiligen Adapters wird `enabled` auf `true` gesetzt.

## Merkmale und Herkunft
Gerichte erhalten eine erweiterbare Liste typisierter Merkmale. Für die Uni-Mensa werden die Kennzeichen aus der CSV-Spalte `kennz` zunächst wie folgt interpretiert:

| Kennzeichen | Merkmal |
| --- | --- |
| `V` | Vegetarisch |
| `VG` | Vegan (gilt im Frontend zugleich als vegetarisch) |
| `MV` | mensaVital |
| `G` | Geflügel |
| `GF` | Glutenfrei |
| `R` | Rind |
| `B` | Bio |
| `S` | Schwein |
| `A` | Enthält Alkohol |
| `L` | Lamm |
| `JD` | Juradistl |

Im HTML-Adapter werden die sichtbaren Icon- und Tooltip-Texte ausgewertet, unter anderem Vegetarisch, Vegan, Bio-Gericht, Schwein, Lamm, Juradistl Lamm und Alkohol. Weitere bekannte Quelllabels wie Wild und Fisch sind ebenfalls abgebildet. Unbekannte Labels werden ignoriert, statt ihnen ungeprüft eine Bedeutung zuzuweisen.

Die Merkmale sollen nicht auf Ernährungsformen beschränkt sein: beispielsweise sind `MV` und `BIO` andere Arten von Kennzeichnungen als `VEGAN`. Die Auswertung muss vollständige Kürzel tokenisieren, damit etwa `V` nicht mit `VG` verwechselt wird. Die am Ende des Gerichtnamens in Klammern aufgeführten Allergene werden zunächst ignoriert und nicht als Merkmale ausgegeben.

Ein Merkmal sollte neben seinem Wert auch seine Herkunft abbilden (z. B. explizite Quellenkennzeichnung, strukturierter Quellcode oder manuelle Korrektur). Nicht vorhandene Angaben gelten als **unbekannt**, nicht als Gegenbeweis.

## Lokal testen
Voraussetzung: Java 21 und Maven. Im Verzeichnis `importer/` `mvn test` bzw. `mvn verify` ausführen.
