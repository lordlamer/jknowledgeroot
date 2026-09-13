# Integrationstests und Betriebsüberwachung

Stand: R13, 12. September 2026.

## Health, Liveness und Readiness

Die Anwendung stellt die folgenden GET-Endpunkte auf ihrem normalen HTTP-/HTTPS-Port
bereit. Die Antworten enthalten den Status und beim Gesamtstatus gegebenenfalls
die Namen der Probe-Gruppen, keine Komponenten, Dateipfade, SQL-/S3-Adressen,
Zugangsdaten oder Exceptiondetails.

| Endpunkt | Bedeutung |
| --- | --- |
| `/actuator/health/liveness` | Der Anwendungsprozess kann Requests bearbeiten; unabhängig von Datenbank und Storage. |
| `/actuator/health/readiness` | Die Anwendung ist gestartet und Datenbank sowie ausgewählter Speicher bestehen ihre Prüfungen. |
| `/actuator/health` | Gesamtstatus einschließlich weiterer automatisch verfügbarer Health-Indikatoren. |

Gesunder Zustand ergibt HTTP 200 mit `{"status":"UP"}`. Ein ausgefallener
Readiness-Bestandteil ergibt HTTP 503 mit `{"status":"DOWN"}`; beim geregelten
Herunterfahren ist auch `OUT_OF_SERVICE` möglich. Während des Starts kann der
Webserver noch gar nicht antworten. Probes benötigen keine Anmeldung und legen
keine Sitzung an. Auch mit einem mitgesendeten Sitzungscookie hängt Liveness
nicht vom JDBC-Sitzungsspeicher ab.
Für diese drei GET-Routen ignoriert die Session-Auflösung vorhandene Login-Cookies.
Damit benötigen auch implizite Session-Abfragen durch MVC keine Datenbankverbindung.
Die Cookie-Konfiguration und die Sitzungs-, Flash- und CSRF-Behandlung der fachlichen
Routen bleiben erhalten.

Readiness prüft MariaDB mit `SELECT 1` über den Laufzeitpool. Die Standardwartezeit
auf eine Poolverbindung beträgt drei Sekunden, die Validierungszeit zwei Sekunden.
Die Probe begrenzt zusätzlich ihr Statement und den Netzwerk-Timeout ihrer
Verbindung auf zwei Sekunden und stellt den vorherigen Netzwerk-Timeout wieder
her. Diese Probe verändert keine Daten und verwendet keine Migrationsrechte.

Beim lokalen Dateispeicher wird im Objektverzeichnis eine kleine `.health-*.tmp`
geschrieben, gelesen und wieder entfernt. Ein fehlendes, nicht schreibbares oder
volles Verzeichnis ergibt DOWN. Bei einem Prozessabbruch kann eine solche
temporäre Datei zurückbleiben; sie unterliegt derselben Wartungsregel wie die
temporären Uploaddateien in [storage.md](storage.md).

Der S3-/MinIO-Treiber prüft die Erreichbarkeit und Existenz seines Buckets mit
einem eigenen, begrenzten HTTP-Client: zwei Sekunden Connect-/Read-Timeout und
drei Sekunden Call-Timeout. Die Probe schreibt keine S3-Objekte. Sie weist daher
weder PutObject-Rechte noch freie Kapazität, Vollständigkeit aller Bestandsobjekte
oder deren Inhalte nach; dafür bleiben Upload-Tests und Backup-/Restoreprüfungen
notwendig. Auch ein blockierendes Netzwerkdateisystem wird durch den lokalen
Dateisystemaufruf nicht mit einem eigenen harten Timeout abgesichert.

Jede Abhängigkeitsprüfung wird innerhalb einer Instanz serialisiert und ihr Ergebnis
zwei Sekunden wiederverwendet. Das begrenzt Probe-Aufrufe bei parallelen
Health-Requests. Ein Zustandswechsel kann entsprechend verzögert sichtbar werden.
Monitoring-Zeitlimits sollten mindestens zehn Sekunden vorsehen; angepasste
Pool-/Netzwerkwerte müssen dazu passen.

## Container und Alarmierung

Das Dockerimage besitzt jetzt einen Healthcheck gegen Readiness. Er läuft alle
30 Sekunden, hat zehn Sekunden Zeitlimit, 45 Sekunden Startfrist und markiert den
Container nach drei aufeinanderfolgenden Fehlern als unhealthy. Das kleine
[Probe-Skript](../deploy/container-healthcheck.sh) benötigt Bash aus dem Basisimage
und prüft den HTTP-Status am internen Loopback-Port 8081 beziehungsweise
`KR_SERVER_PORT`.

Die Standardkonfiguration passt zum Produktions-Compose mit TLS am vorgeschalteten
Proxy. Bei direktem TLS im Container, anderem Kontextpfad oder abweichendem Port
den Container-Healthcheck passend überschreiben. Actuator-Basispfad und
Healthpfade nicht ohne Anpassung der Security-Regeln verändern.

`docker compose up --wait` wartet damit auf einen tatsächlich bereiten App-Container.
Compose startet einen lediglich unhealthy markierten Container mit
`restart: unless-stopped` nicht automatisch neu. Ein Datenbank-/S3-Ausfall soll
alarmieren und gegebenenfalls Traffic aus der Instanz nehmen. Für automatische
Neustarts Liveness verwenden; Readiness-Ausfälle abhängiger Dienste sind kein
geeigneter Anlass für eine Neustartschleife.

Vor dem produktiven Einsatz in der eigenen Überwachung einrichten:

- Readiness-Fehler beziehungsweise fehlende Antworten über mehrere Prüfintervalle
  alarmieren und UP nach Wiederherstellung bestätigen.
- Freien Platz sowohl im persistenten Datei-Volume als auch im temporären
  Uploadspeicher überwachen; zusätzlich Datenbankgröße, Backup-Alter und Zertifikate.
- HTTP-5xx-Rate, Antwortzeiten, JVM-Auslastung und Poolengpässe mit zur eigenen
  Last passenden Schwellen überwachen. Grenzwerte sind noch nicht unter
  Produktionslast vermessen.

## Metriken und Schutz

Spring Boot Actuator/Micrometer liefert JVM-, Prozess-, HTTP- und
Datenbankpoolmetriken. Beispiele für angemeldete Administratoren:

- `/actuator/metrics/jvm.memory.used`
- `/actuator/metrics/http.server.requests`
- `/actuator/metrics/hikaricp.connections.active`
- `/actuator/metrics/knowledgeroot.dependency.available`

Die letzte Gauge verwendet nur die festen Tags `dependency=database` und
`dependency=storage`: `1` bedeutet letzte Prüfung erfolgreich, `0` fehlgeschlagen,
`-1` noch nicht geprüft. Die Gauge löst selbst keine Probe aus; regelmäßig
Readiness abfragen. Es werden keine Tags aus Dateinamen, Benutzerkennungen oder
SQL-/S3-URLs erzeugt.

Anonyme Metrikzugriffe werden zur Anmeldung umgeleitet, normale Benutzer erhalten
403. Ein Rollenentzug wird über die bestehende Sitzungsprüfung berücksichtigt.
Der Health-Zugriff nutzt eine eigene zustandslose Filterkette. Alle anderen
Actuator-Pfade verlangen Administratorrechte; zusätzlich sind standardmäßig
ausschließlich Health und Metrics als lesbare Endpunkte freigegeben.
`env`, `configprops`, Heapdump, Logdateien und schreibende Managementoperationen
sind nicht exponiert. JMX-Exposition ist deaktiviert.

Es gibt noch keinen separaten Maschinenzugang, Prometheus-Exporter oder extern
eingerichtetes Dashboard. Die vorhandenen Metriken dienen zunächst der geschützten
Diagnose. Für automatisiertes Scraping ein eigenes Zugangs-/Netzwerkkonzept
ergänzen; keine Administratorpasswörter oder Sitzungen in öffentliche Scrape-URLs
übernehmen und die Metrik-Endpunkte nicht pauschal anonym freigeben.

## Logs

Zustandswechsel einer Abhängigkeit werden einmal als UP (INFO) beziehungsweise
DOWN (WARN) protokolliert. Wiederholte Abfragen desselben Zustands erzeugen keine
zusätzlichen Meldungen dieser Probe. Fehlertexte und Exceptions der Probe werden
nicht übernommen. Bei DOWN zunächst Dienstzustand, Zugriffsrechte und Kapazität
prüfen; normale fehlgeschlagene Fachrequests haben weiterhin ihre Fehlerlogs.
Das Produktionsprofil unterdrückt bereits seit R12 die Hibernate-Verbindungsübersicht,
deren JDBC-Metadaten-URL ein Passwort enthalten kann.

Logs nach außen sammeln und ihre Aufbewahrung sowie Zugriffsrechte im Betrieb
festlegen. Globale SQL-, HTTP-Header-/Body- oder Security-TRACE-Protokollierung
kann sensible Daten enthalten und ist kein Bestandteil dieser Betriebsanleitung.

## Integration und Nachweise

Die beiden bisher deaktivierten Klassen `PageControllerIntegrationTest` und
`PageEditPermissionIntegrationTest` sind durch aktive Tests mit echtem
Spring-Kontext, MariaDB, Dateispeicher, Login/CSRF und JDBC-Sitzungen ersetzt.
Die Tests haben eigene Datensätze statt fester Benutzer-/Seiten-IDs und lesen
keine lokale `.env` oder IDE-Datenbankkonfiguration. Synthetische Controller der
Security-Einzeltests sind als Testkomponenten markiert und werden nur dort
ausdrücklich importiert.

Die Integration deckt Gruppenmitgliedschaft und deren Entzug, Seitenrechte und
Vererbung, Berechtigungsänderungen samt Rollback, Kommentare und deren Eigentümer,
Sterne, Labels und Dateizugriffe ab. Health-/Metrikschutz wird einschließlich
Rollenentzug geprüft. Bestehende MariaDB-Installationstests prüfen Neuinstallation
und Upgrade unter Erhalt von Inhalten, Konten, Freigaben, Dateien und Sitzungen.

Der JAR-/HTTPS-/Browsertest pausiert echte, entbehrliche MinIO- und MariaDB-Container.
Er prüft DOWN/503 bei Readiness, weiterhin UP bei Liveness und die Erholung nach
dem Fortsetzen. Dasselbe wird mit einem vorübergehend verschobenen lokalen
Speicherverzeichnis geprüft. Der Compose-Test startet das fertige Image und
führt den eingebauten Healthcheck auch nach einem Neustart aus.

Die Prüfungen ersetzen keine gehostete CI-Abnahme, Lastmessung, externe
Alarmierung oder erprobte vollständige Wiederherstellung. Diese Betriebsaufgaben
und die Release-Abnahme bleiben in der [Roadmap](roadmap.md) sichtbar.

Referenzen: [Spring Boot: Actuator-Endpunkte und Security](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html),
[Spring Boot: Metriken](https://docs.spring.io/spring-boot/reference/actuator/metrics.html).
