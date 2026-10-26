# Passauer Speisekarten

Statischer Aggregator für Mittags-, Tages- und Wochenkarten in Passau.

## Architektur
- `importer/`: Java 21 / Maven für Abruf, Normalisierung und JSON-Export.
- `config/sources.csv`: konfigurierbare Kartenquellen.
- `corrections/menu-decisions.json`: persistente manuelle Korrekturen.
- `frontend/`: statische Webseite und veröffentlichte JSON-Dateien für Cloudflare Pages.
- `.github/workflows/`: CI und geplanter Import.

Die erste Version benötigt keine Datenbank. Git hält Änderungen an Konfiguration und Daten nachvollziehbar fest.

## Lokal testen
Voraussetzung: Java 21 und Maven.

```bash
mvn -f importer/pom.xml verify
```

Der Importer ist zunächst ein Gerüst. Vor dem produktiven Zeitplan müssen die Quellenadapter, Validierung und der JSON-Export implementiert und getestet werden.

## Cloudflare Pages
Repository verbinden, Produktionsbranch `main`, kein Build-Befehl, Output-Verzeichnis `frontend`.

## Qualitätsregeln
- Abrufzeitpunkt und Gültigkeit einer Karte getrennt halten.
- Unsichere oder abgelaufene Karten nicht als aktuell ausgeben.
- Manuelle Korrekturen getrennt von generierten Daten speichern.
- Bei Importfehlern die letzte gültige Veröffentlichung behalten.
