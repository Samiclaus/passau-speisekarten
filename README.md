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
- **Uni-Mensa Passau (CSV):** Wochenplan unter `https://www.stwno.de/infomax/daten-extern/csv/UNI-P/{isoWeek}.csv`; `{isoWeek}` steht für die aktuelle ISO-Kalenderwoche. Die Spalte `kennz` liefert strukturierte Kennzeichen.
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
| `L` | Vermutlich Lamm; Zuordnung noch zu bestätigen |
| `JD` | Vermutlich Juradistl; Zuordnung noch zu bestätigen |

Die Merkmale sollen nicht auf Ernährungsformen beschränkt sein: beispielsweise sind `MV` und `BIO` andere Arten von Kennzeichnungen als `VEGAN`. Die Auswertung muss vollständige Kürzel tokenisieren, damit etwa `V` nicht mit `VG` verwechselt wird. Die am Ende des Gerichtnamens in Klammern aufgeführten Allergene werden zunächst ignoriert und nicht als Merkmale ausgegeben.

Ein Merkmal sollte neben seinem Wert auch seine Herkunft abbilden (z. B. explizite Quellenkennzeichnung, strukturierter Quellcode oder manuelle Korrektur). Nicht vorhandene Angaben gelten als **unbekannt**, nicht als Gegenbeweis.

## Lokal testen
Voraussetzung: Java 21 und Maven.

```bash
mvn -f importer/pom.xml verify
```

Der Importer ist zunächst ein Gerüst. Vor dem produktiven Import müssen die Quellenadapter, Validierung und der JSON-Export implementiert und getestet werden.

## Scheduler
Der Workflow ist für **07:00 und 10:00 Uhr Europe/Berlin** vorgesehen. GitHub Actions interpretiert Cron-Zeitpläne in UTC; deshalb löst er viermal pro Tag einen kurzen Zeitzonencheck aus und führt Import/Commit nur bei 07:00 oder 10:00 Ortszeit aus. Ein manueller Start über `workflow_dispatch` läuft unabhängig von der Uhrzeit.

## Cloudflare Pages
Repository verbinden, Produktionsbranch `main`, kein Build-Befehl, Output-Verzeichnis `frontend`.

## Qualitätsregeln
- Abrufzeitpunkt und Gültigkeit einer Karte getrennt halten.
- Unsichere oder abgelaufene Karten nicht als aktuell ausgeben.
- Manuelle Korrekturen getrennt von generierten Daten speichern.
- Bei Importfehlern die letzte gültige Veröffentlichung behalten.
