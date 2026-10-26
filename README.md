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
- **I Sapori della Toscana (HTML):** `https://sapori-toscana.com/` zeigt Mittagsgerichte nach Wochentag samt Preisen. Der HTML-Importer liest Gerichte, Preise und optionale Zutatenbeschreibungen aus den Wochentagsblöcken und ersetzt die Einträge dieser Quelle beim Import. Er berücksichtigt das Referenzdatum bis einschließlich Freitag; historische und veraltete Einträge dieser Quelle werden entfernt. Die heuristische Merkmalsklassifikation erkennt unter anderem Nduja/Salsiccia/Speck, Salmone/Tonno, Carne/Ragù sowie deutsche Begriffe für Fleisch und Fisch. Vegetarisch wird nur dann automatisch ergänzt, wenn eine Beschreibung vorhanden ist und keine Fleisch-/Fischbegriffe erkannt wurden; das bleibt eine Heuristik und keine garantierte Zutatenprüfung. Die Quelle bleibt bis zum lokalen Live-Test deaktiviert.
- **Wirtshaus & Pension Goldenes Schiff (HTML/PDF):** `https://goldenesschiff.de/` zeigt einen Mittagstisch-Block und eine Wochenkarte als PDF. Der erste Importer liest datierte Gerichte aus der Wochenkarte aus und akzeptiert historische Daten. Der Preis von 11,80 € wird derzeit dem Hauptgericht zugeordnet; der HTML-Tagesblock ist noch nicht als Fallback angebunden. Vor Aktivierung muss der Import anhand der aktuellen PDF lokal geprüft werden.

Die Einträge in `config/sources.csv` sind zunächst deaktiviert. Erst nach Implementierung und Tests des jeweiligen Adapters wird `enabled` auf `true` gesetzt.

## Importstatus und Diagnose

Die statische Statusseite liegt unter `frontend/status.html` und ist von der Speisekartenübersicht verlinkt. Ihre Daten kommen aus `frontend/import-status.json`. Dort sind Schemafelder für letzten Versuch, letzten erfolgreichen Import, Menü-/Gerichtszahlen, aktivierte Quellen und Hinweise pro Quelle vorgesehen.

Aktuell ist das Statusdokument ein initialer Stand: Importversuche und Ergebnisse werden noch nicht automatisch protokolliert. Deshalb werden unbekannte Zeitpunkte und Zählwerte ausdrücklich als „noch nicht erfasst“ angezeigt und nicht als erfolgreicher Import ausgegeben.

Der manuelle Workflow liegt unter `.github/workflows/manual-menu-import.yml` und kann über die Importstatusseite oder direkt in GitHub Actions gestartet werden. Er bietet die Uni-Mensa-HTML-Quelle und Pomeranz zur Auswahl. Der Aufruf benötigt ein GitHub-Konto mit Schreibzugriff auf das Repository; es werden keine Zugangsdaten in der öffentlichen Website hinterlegt.

Nach jedem Lauf protokolliert `.github/scripts/update_import_status.py` den Versuch, den Ausgang und die in den veröffentlichten JSON-Daten vorhandenen Menü-/Gerichtszahlen. Auch fehlgeschlagene Läufe aktualisieren den Status; dabei werden `menus.json` und `restaurants.json` durch den Statusschritt nicht verändert. Erfolgreiche Importläufe veröffentlichen Menü- und Statusdaten gemeinsam per Git-Commit. Ein Ergebnis `NO_DISHES` wird angezeigt, wenn ein Lauf erfolgreich war, aber danach keine Gerichte dieser Quelle in den veröffentlichten Daten vorhanden sind.

Hinweis: Der Statuszähler zählt veröffentlichte Daten für die ausgewählte Quelle, nicht ausschließlich neu hinzugefügte Datensätze. Die Importer müssen weiterhin vor dem Schreiben prüfen, ob eine Quelle aktuell und ausreichend vollständig ist. Der Workflow ist manuell; ein Zeitplan ist bewusst noch nicht aktiviert.

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
Voraussetzung: Java 21 und Maven. **Alle Befehle werden im Repository-Hauptverzeichnis** ausgeführt (dort, wo `frontend/`, `importer/` und `config/` liegen). Git-Befehle und Maven-Aufrufe verwenden damit dieselbe Verzeichnisebene.

Bei aktivierter Zscaler-TLS-Inspection zuerst in jeder neuen PowerShell-Sitzung den bereits eingerichteten lokalen Truststore setzen. Der Truststore liegt unter `%LOCALAPPDATA%\PassauSpeisekarten\truststore.jks`; das Zertifikat muss nicht erneut importiert werden:

```powershell
$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStore=$env:LOCALAPPDATA\PassauSpeisekarten\truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"

git pull
mvn -f importer/pom.xml clean verify
```

### Pomeranz-PDF prüfen und importieren

Die Pomeranz-Quelle sucht auf der offiziellen Speisekartenseite nach der aktuellen PDF mit dem Label „Mittagskarte“. Der Parser berücksichtigt den Zeitraum ohne Jahresangabe im PDF-Text und übernimmt das Jahr aus dem datierten Dateinamen. Er extrahiert das vegetarische Wochengericht sowie die Tagesgerichte aus den Abschnitten „Am Dienstag/Mittwoch/… in Kombination mit“.

Zuerst kann der Livecheck den erkannten Zeitraum, die Mittagszeit, den Kombipreis und den PDF-Text ausgeben, ohne Dateien zu verändern:

```powershell
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  '-Dexec.mainClass=de.passau.speisekarten.PassauSpeisekartenApplication' `
  '-Dexec.args=check-pomeranz'
```

Der eigentliche Import wird mit `import-pomeranz` gestartet. Er ergänzt die bestehenden JSON-Dateien und erhält Menü- und Restaurantdaten anderer Quellen. Bei fehlendem Preis, fehlenden Servicezeiten, einem abgelaufenen PDF oder unzureichend erkannten Gerichten bricht er vor dem Schreiben ab. Pomeranz bleibt in `config/sources.csv` deaktiviert, bis der Import lokal geprüft wurde:

```powershell
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  '-Dexec.mainClass=de.passau.speisekarten.PassauSpeisekartenApplication' `
  '-Dexec.args=import-pomeranz'
```

Die offizielle Website nennt für Pomeranz regulär Dienstag bis Samstag 11:30–14:00 und 17:30–23:00 Uhr; Sonntag und Montag sind geschlossen. Für Feiertage werden dieselben Zeiten genannt. Diese Angaben werden im Restaurantdatensatz getrennt als Öffnungs- und Servicezeiten abgelegt; konkrete datierte Ausnahmen sind noch nicht automatisch erfasst. Quelle: https://www.restaurant-pomeranz.de/Speisekarte/

### Uni-Mensa live prüfen
Der manuelle Diagnose-Runner ruft den echten STWNO-HTML-POST-Endpunkt für die Uni-Mensa Passau (`UNI-P`) für sieben aufeinanderfolgende Tage ab: vom Startdatum einschließlich bis sechs Tage später. Standardmäßig beginnt der Zeitraum heute; so entspricht ein Lauf am Montag dem Zeitraum Montag bis Sonntag. Für jeden Tag werden Kategorie, Gericht, Kennzeichen sowie alle drei Preise (Studierende, Beschäftigte, Gäste) ausgegeben. Eine Wochenübersicht fasst Gerichte, fehlende Angaben und Abruffehler zusammen. Der Runner schreibt keine Dateien und aktiviert keine Quelle.

Im Repository-Hauptverzeichnis ausführen:

```powershell
# Heute und die folgenden sechs Tage
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  -Dexec.mainClass=de.passau.speisekarten.mensa.MensaHtmlLiveCheck

# Optional mit festem Startdatum (Beispiel: Montag bis Sonntag)
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  -Dexec.mainClass=de.passau.speisekarten.mensa.MensaHtmlLiveCheck `
  '-Dexec.args=2026-10-26'
```

Ein Tag ohne erkannte Gerichte wird mit Diagnose ausgegeben und zählt nicht automatisch als Parserfehler, da nicht jeder Tag zwingend einen Speiseplan haben muss. Abruffehler werden für den jeweiligen Tag ausgewiesen; die übrigen Tage werden trotzdem geprüft. Der Livecheck benötigt eine funktionierende Internetverbindung; die normalen Unit-Tests benötigen keinen Zugriff auf STWNO.

### Uni-Mensa importieren und JSON erzeugen

Der erste ausführbare Importpfad liest die Uni-Mensa Passau über den HTML-POST-Endpunkt ein und erzeugt die beiden JSON-Dateien, die das Frontend direkt lädt: `frontend/menus.json` und `frontend/restaurants.json`. Es werden sieben Tage ab heute verarbeitet; Gerichte mit fehlenden Preisen bleiben enthalten und die fehlenden Preisstufen werden als `null` exportiert.

Im Verzeichnis `importer/` ausführen:

```powershell
# Sieben Tage ab heute
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  '-Dexec.mainClass=de.passau.speisekarten.PassauSpeisekartenApplication' `
  '-Dexec.args=import-mensa-html'

# Optional: fester Starttag und Anzahl der Tage
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  '-Dexec.mainClass=de.passau.speisekarten.PassauSpeisekartenApplication' `
  '-Dexec.args=import-mensa-html 2026-10-26 7'
```

Die oben gesetzte `MAVEN_OPTS`-Variable stellt den lokalen Java-Truststore auch für diese Maven-Aufrufe bereit. Der Import schreibt die JSON-Dateien erst nach erfolgreichem Abruf aller angeforderten Tage; bei einem Abruffehler bleiben die bisherigen Dateien unverändert. Die Importquelle ist noch nicht für einen automatischen Zeitplan aktiviert.



### I Sapori della Toscana importieren

Der Import verwendet die Wochentagsblöcke der offiziellen HTML-Seite. Für den aktuellen Referenztag und die folgenden Wochentage bis Freitag werden Gerichte mit Name, Preis und optionaler Zutatenbeschreibung eingelesen. Das Referenzdatum kann zum reproduzierbaren Testen übergeben werden.

Im Repository-Hauptverzeichnis ausführen:

```powershell
# Import ab heute bis Freitag
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  '-Dexec.mainClass=de.passau.speisekarten.PassauSpeisekartenApplication' `
  '-Dexec.args=import-sapori-toscana'

# Reproduzierbarer Test, z. B. Donnerstag bis Freitag
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  '-Dexec.mainClass=de.passau.speisekarten.PassauSpeisekartenApplication' `
  '-Dexec.args=import-sapori-toscana 2026-10-29'
```

Der Import ersetzt alle bisherigen Einträge von i Sapori della Toscana, sodass historische Daten nicht stehen bleiben. Vor dem Schreiben wird geprüft, ob für jeden erwarteten Wochentag mindestens ein Gericht mit Preis gefunden wurde. Bei unvollständiger Seite oder einem Fehler bleiben die vorhandenen JSON-Dateien unverändert. Samstags und sonntags wird der Import abgebrochen, da die Mittagskarte nur für Montag bis Freitag ausgewiesen ist.

### Alle implementierten Quellen importieren

Der Sammelbefehl `import-all` führt die derzeit vorgesehenen Quellen Uni-Mensa HTML und Pomeranz nacheinander aus. Goldenes Schiff bleibt zunächst deaktiviert in `config/sources.csv`; der separate Importbefehl dient erst einmal der lokalen Prüfung und wird nicht von `import-all` gestartet.

```powershell
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  '-Dexec.mainClass=de.passau.speisekarten.PassauSpeisekartenApplication' `
  '-Dexec.args=import-all'
```

Einzelner Goldenes-Schiff-Import zur lokalen Prüfung:

```powershell
mvn -f importer/pom.xml -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  '-Dexec.mainClass=de.passau.speisekarten.PassauSpeisekartenApplication' `
  '-Dexec.args=import-goldenes-schiff'
```
