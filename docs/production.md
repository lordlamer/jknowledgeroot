# Produktionskonfiguration und Lieferprozess

Stand: R22, 13. September 2026. Dieser Stand bereitet den Betrieb vor;
[Backup und Wiederherstellung](recovery.md) sowie der [Release-Kandidat](release.md)
sind dokumentiert; die betriebliche Freigabe bleibt offen. Versionskonflikte,
Historie und Löschverhalten beschreibt [page-history.md](page-history.md). Healthchecks, Metriken und
die erweiterten Integrationstests beschreibt [monitoring.md](monitoring.md).
Der [OS-Imagescan](image-security.md) ergänzt die Paketprüfung. Für die tatsächliche
Zielumgebung steht ein [Abnahmeprotokoll](operational-acceptance.md) bereit.
Die verbleibenden nativen [Datenbankbefunde](database-findings.md) sind gesondert
bewertet. Das Datenbankimage enthält kein GPG-/SQLite-Werkzeugpaket; eigene
Initialisierungs- und Wartungsskripte dürfen diese nicht voraussetzen.

## Betriebsmodell

Der dokumentierte Standard ist **eine Anwendungsinstanz mit lokalem Dateispeicher
auf einem persistenten Volume**, MariaDB und einem vorgeschalteten HTTPS-Proxy.
Damit benötigt der Betrieb keinen MinIO-Community-Server. Für mehrere Instanzen
ist ein gemeinsam verfügbarer, gepflegter S3-Dienst gesondert auszuwählen und mit
dem vorhandenen MinIO-SDK-Treiber zu erproben. Ein Treiberwechsel kopiert keine
Bestandsdateien; siehe [storage.md](storage.md).

Das bisherige `docker-compose.yaml` bleibt eine Entwicklungsumgebung für IDE-Starts.
Seine Datenbank-/MinIO-Ports sind jetzt nur auf Loopback gebunden, Images versioniert
und Zugangsdaten verpflichtend. Das ändert keine Passwörter vorhandener Volumes.
Die Produktionsdatei [compose.production.yaml](../deploy/compose.production.yaml)
ist eine getrennte, neue Installation. Bestehende Entwicklungsvolumes nicht
ungeprüft umhängen; deren Konten und Daten müssen regulär migriert werden.

Neue Produktionsumgebungen verwenden ein gemeinsam mit der Anwendung gebautes
MariaDB-**12.3.3-LTS**-Image mit zusätzlichen Ubuntu-glibc-Korrekturen. Seine
Herstellerbasis ist auf einen festen Digest gepinnt. Die Reihe wird laut
[MariaDB-Releasehinweisen](https://mariadb.com/docs/release-notes/community-server/12.3/12.3.3)
bis Juni 2029 gepflegt. **`KR_DB_IMAGE` ist verpflichtend**, ein leerer Wert bricht
die Compose-Konfiguration ab. Nach Veröffentlichung den im Release erfassten
Datenbank-Digest verwenden; für Rollback ausdrücklich die gesicherte alte Version.
Für bestehende 12.2.2-Installationen vor dem nächsten
Compose-Start die alte Imageversion in der Env-Datei festhalten und den
[logischen Umstieg in neue Volumes](recovery.md#wechsel-von-mariadb-1222-auf-1233)
verwenden. Der geänderte Standard ist keine Freigabe für ein Upgrade bestehender
Datenbankdateien. Das Entwicklungs-Compose bleibt dafür zunächst auf 12.2.2.

## Neue Umgebung starten

1. Temurin JDK 25.0.4.1+1, Docker mit Compose, einen persistenten Datenträger und einen Host mit
   funktionierendem HTTPS-Proxy bereitstellen. Für einen lokalen Build
   die Befehle mit Versions-/Revisionsnachweis aus [release.md](release.md) verwenden.
   Ein freigegebenes Image später mit
   unveränderlichem Digest statt eines beweglichen Tags referenzieren.
2. [production.env.example](../deploy/production.env.example) außerhalb des
   Repositorys in eine zugriffsgeschützte Datei kopieren. `KR_APP_IMAGE` konkret
   und `KR_DB_IMAGE` aus demselben Release setzen. Individuelle Passwörter für root, Laufzeitkonto und Migration vergeben.
   Die beiden zuletzt genannten Werte müssen für das Initialisierungsskript
   mindestens 32 Zeichen aus `A–Z`, `a–z`, `0–9`, `_`, `-` enthalten; beispielsweise
   jeweils `openssl rand -hex 32` verwenden. Die Anwendung selbst verlangt diese
   eingeschränkte Zeichenmenge nicht für extern eingerichtete Datenbanken.
3. `KR_BOOTSTRAP_LOGIN` und ein individuelles Bootstrap-Passwort setzen; die
   Regeln aus [installation.md](installation.md) gelten weiterhin. Keine
   Zugangsdaten in Kommandozeilenargumente, Git oder Containerimages schreiben.
4. Mit expliziter Datei und Projektname zunächst prüfen und danach starten:

   ```sh
   docker compose --project-name knowledgeroot-prod --env-file /secure/knowledgeroot.env -f deploy/compose.production.yaml config --quiet
   docker compose --project-name knowledgeroot-prod --env-file /secure/knowledgeroot.env -f deploy/compose.production.yaml up -d
   ```

5. Den Proxy wie unten konfigurieren und über die HTTPS-Adresse anmelden.
   Bootstrap-Werte danach aus der Konfiguration entfernen und den App-Container
   neu erstellen. Passwörter in der Env-Datei ändern keine Konten einer bereits
   initialisierten Datenbank; Passwortrotation benötigt zusätzlich die passende
   Datenbankänderung. Die Datenbank-Initialisierung läuft nur bei leerem Volume.

Nur `127.0.0.1:8081` wird auf dem Host veröffentlicht; Datenbank und Dateien haben
keine öffentlichen Ports. App-Port und bestehende Dienste vor dem Start auf
Kollisionen prüfen. Die Compose-Datei verwendet eigene benannte Volumes. Beim
regulären Stoppen niemals `down --volumes` einsetzen: Das würde Daten löschen.
Backups müssen Datenbank und Objekte zusammen erfassen; Skripte und Restore-Abnahme
stehen in [recovery.md](recovery.md).

Das Image verwendet Temurin `25.0.4_7-jre-noble` mit festem Digest und startet als
UID/GID `10001:10001`. Eine gezielte Ubuntu-glibc-Aktualisierung ergänzt die im
Basisimage noch fehlende Korrektur; Version und Scan sind in [image-security.md](image-security.md)
beschrieben.
System-Schriftverwaltung und deren Expat-Abhängigkeit sind für den vorhandenen
Webserver entfernt; serverseitiges Schriftrendering benötigt eine gesonderte
Abhängigkeitsprüfung. [container-findings.md](container-findings.md) bewertet die
verbleibenden Befunde. Das Root-Dateisystem ist im Produktions-Compose nur lesbar;
Capabilities sind entfernt und Privilegienausweitung deaktiviert. Ein neues
Datei-Volume übernimmt die Besitzrechte aus dem Image. Vorhandene Bind-Mounts
müssen passend für UID 10001 eingerichtet sein. `/tmp` ist ein begrenztes
512-MiB-tmpfs; parallele große Uploads benötigen mehr Platz oder ein geeignetes
privates temporäres Volume. Grenzen stehen in [storage.md](storage.md).

Seit R13 prüft der eingebaute Container-Healthcheck Readiness einschließlich
Datenbank und Speicher. `up --wait` wartet auf diesen Zustand. Ein unhealthy-Status
löst unter Compose keine automatische Neustartschleife aus; Betriebsregeln und
Anpassungen bei direktem TLS stehen in [monitoring.md](monitoring.md).

## Produktionsprofil und Datenbankrechte

Das Image aktiviert `production`; beim direkten JAR-Start
`--spring.profiles.active=production` verwenden. Folgende Werte sind verpflichtend:

Beim direkten JAR-Start außerdem `KR_STORAGE_DRIVER=file` und ein persistentes
`KR_FILE_STORAGE_DIR` setzen; Compose setzt diese beiden Werte bereits.

| Variable | Zweck |
| --- | --- |
| `KR_DB_URL` | URL einer bereits angelegten MariaDB-Datenbank |
| `KR_DB_USER`, `KR_DB_PASSWORD` | Eigenes Laufzeitkonto |
| `KR_MIGRATION_USER`, `KR_MIGRATION_PASSWORD` | Eigenes Migrationskonto auf derselben Datenbank |

root und identische Benutzernamen für beide Aufgaben werden abgelehnt. Die
Anwendung prüft fehlende Einstellungen früh, ohne deren Werte auszugeben. Im
Produktionsprofil sind Template-Cache, Secure-/HttpOnly-Sitzungscookies und
SameSite=Lax aktiv. Das Deaktivieren dieser Schutzeinstellungen, Demodaten oder
die Kombination mit `development` verhindert den Start. Lokale IntelliJ-Starts
im Entwicklungsprofil bleiben möglich.
Die Hibernate-Verbindungsübersicht wird im Produktionsprofil unterdrückt, weil
MariaDB-Metadaten-URLs das Passwort enthalten können. Starttests prüfen auch,
dass keine JDBC-Passwortparameter im Log erscheinen.

Das Laufzeitkonto erhält ausschließlich `SELECT, INSERT, UPDATE, DELETE` auf
`knowledgeroot.*`. Liquibase verwendet eine separate Verbindung mit
`SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES` auf
derselben Datenbank. Keines der beiden Konten benötigt globale Rechte,
Benutzerverwaltung oder `GRANT OPTION`. Datenbank/Benutzer werden vorab vom
Betreiber beziehungsweise dem Initialisierungsskript eingerichtet. Der
Erst-Administrator wird anschließend über das Laufzeitkonto angelegt.

Liquibase läuft weiterhin beim Anwendungsstart. Deshalb sind die Migrationsdaten
im gestarteten Prozess vorhanden und bei jedem Start erforderlich. Die Trennung
begrenzt das verwendete Laufzeitkonto, entfernt aber nicht sämtliche DDL-Zugangsdaten
aus dem Prozess. Eine spätere Auslagerung in einen separaten Migrationsjob benötigt
eine explizite Schema-Versionsprüfung. Vor Upgrades andere Instanzen stoppen.

## HTTPS, Proxy-Vertrauen und Cookies

Clients greifen ausschließlich über HTTPS zu. Der App-Port ist nur für den
vertrauenswürdigen Proxy bestimmt; reines HTTP ist kein nutzbarer Produktionslogin,
weil Sitzungscookies als Secure ausgegeben werden. Alternativ kann das JAR über
die regulären `server.ssl.*`-Einstellungen selbst TLS terminieren.

Standardmäßig ignoriert `KR_FORWARD_HEADERS_STRATEGY=none` Forwarded-Header.
Für einen vorgeschalteten Proxy `KR_FORWARD_HEADERS_STRATEGY=native` und
`KR_TRUSTED_PROXY_PATTERN` auf einen Java-Regulärausdruck **der tatsächlich am
App-Socket sichtbaren Proxy-Adresse** setzen. Beispiel für genau `192.0.2.10`:
`192[.]0[.]2[.]10`. Bei Docker-NAT kann dies die Bridge-Gateway-Adresse sein,
nicht `127.0.0.1`. Diese Adresse in der konkreten Umgebung feststellen und
Netzwerkzugriff entsprechend begrenzen. Keine pauschalen privaten Netzbereiche
oder `.*` vertrauen. Der Default `(?!)` vertraut niemandem; native ohne
angepasste Vertrauensregel wird abgelehnt.

Das [nginx-Beispiel](../deploy/nginx.conf.example) ersetzt fremde Forwarded-Header
am einzigen öffentlichen Eingang und setzt Schema, Client-IP und Port selbst.
Hostname/Zertifikate anpassen und nginx-Konfiguration vor Übernahme prüfen.
Tomcat verarbeitet nur Header einer vertrauenswürdigen Gegenstelle und löst
Proxy-Ketten von rechts nach links auf. Diese effektive Gegenstellenadresse
verwendet auch die Login-Drosselung. Bei `none` teilen Clients hinter einem
Proxy dessen Quoten. Weitere Proxys verlangen eine bewusst angepasste Vertrauenskette.

Abnahme am tatsächlichen Host: HTTP leitet auf HTTPS um, Login/Logout funktionieren,
SESSION-Cookies tragen Secure/HttpOnly/SameSite, HTTPS-Antworten enthalten HSTS,
Redirects bleiben auf der öffentlichen HTTPS-Adresse und fremde
`X-Forwarded-For`-Werte verändern keine Login-Quote. Ein Proxy-/Zertifikatssetup
für die konkrete Domain ist nicht durch die Konfigurationsdatei allein erledigt.

Der [lokale HTTPS-Proxytest](proxy-testing.md) prüft das nginx-Beispiel mit eigenen
Testzertifikaten, Login/Logout, Cookies, Redirects und gefälschten Forwarded-Headern
gegen das Produktions-Compose. Er ergänzt die CI; die Abnahme am Zielhost bleibt
weiterhin erforderlich.

## CI und Veröffentlichung

Der GitHub-Workflow prüft PRs und `master` mit Leserechten, Maven-Verify, Containerbuild
und einem entbehrlichen Compose-Start samt Neustart. PRs erhalten keine Registry-
Zugangsdaten und veröffentlichen keine Images. Alle verwendeten Actions sind auf
vollständige Commit-SHAs festgelegt. Die Actions-Versionen und Basisimages müssen
weiterhin regelmäßig geprüft und aktualisiert werden.

Nur Pushes eines Tags wie `v1.0.0` oder `v1.0.0-rc.2` können den separaten
Publish-Job starten. Davor wird geprüft, dass der Commit zu `origin/master`
gehört und der gesamte Verify-Job erfolgreich war. Der Publish-Job lädt exakt
die dort erzeugten App-/Datenbankimages und veröffentlicht Versions- und Commit-Tags
gemäß [release.md](release.md); kein
`latest` und kein erneuter Build mit möglicherweise verändertem Inhalt.

Im Repository verhindern seit dem 19. September aktive Rulesets Force-Pushes
und Löschen von `master` sowie Ändern und Löschen bestehender `v*`-Tags.
Zusätzlich verlangt `master` einen Pull Request mit erfolgreichem `verify` und
aktuellem Basisbranch; eine fremde Review-Freigabe ist beim alleinigen Entwickler
nicht erforderlich. Die GitHub-Umgebung `release` verlangt die eigene Bestätigung
durch `lordlamer`, erlaubt nur Tags `v*` und keinen Administrator-Bypass.
**Noch umzustellen** sind `DOCKERHUB_USERNAME` und `DOCKERHUB_TOKEN`: Beide liegen
derzeit als Repository-Secrets vor. Direkt in GitHub als Environment-Secrets in
`release` hinterlegen, anschließend die Repository-Kopien entfernen und prüfen.
Das bestehende Imageziel ist `lordlamer/knowledgeroot`; bei einem Fork anpassen.
Der Workflow liest die Release-Regeln vor einem Tag-Build und nochmals vor dem
Registry-Login: Ohne Reviewer und die ausschließliche Tag-Regel `v*` bricht er ab.
Die eigentliche Freigabe erzwingt GitHub; die Secret-Ablage und der tatsächliche
Publish-Ablauf bleiben Teil der Betriebsabnahme. Das bestätigte Freigabemodell
und die genauen Einstellungen stehen in
[repository-protection.md](repository-protection.md).
Eine Veröffentlichung wurde nicht ausgeführt.
Der separate Travis-Workflow bleibt eine reine Prüfung ohne Veröffentlichung.

## Prüfungen und Grenzen

`ProductionConfigTest` prüft Pflichtwerte und unsichere Overrides. Der bestehende
JAR-/Browsertest verwendet jetzt ein isoliertes TLS-Zertifikat, getrennte
Migrationszugänge und einen echten MariaDB-Laufzeitbenutzer ohne DDL-Rechte.
Er prüft Cookies/HSTS, Anmeldung und Neustart, alte Funktionen, beide Speicherwege
sowie ignorierte beziehungsweise vertrauenswürdig weitergeleitete Client-IP-Ketten.

`sh deploy/smoke-test.sh knowledgeroot:verified knowledgeroot:database-verified` erzeugt ein zufällig benanntes,
entbehrliches Compose-Projekt mit eigenen Passwörtern und Volumes und entfernt
**nur dieses Testprojekt einschließlich seiner Volumes** beim Ende. Es prüft
Ersteinrichtung, HTTP-Erreichbarkeit des internen App-Ports, UID, Schreibrechte,
verweigerte DDL und Neustart. Das ersetzt keinen HTTPS-Test des produktiven Proxys.
Gehostete GitHub-Ausführung, Registry-Push, Domain/Zertifikate und die endgültige
Freigabe der tatsächlichen Betriebsumgebung bleiben externe Abnahmen. Der zusätzliche
Restore-/Upgrade-/Rollbacktest ist in [recovery.md](recovery.md) beschrieben.

Referenzen: [Spring Boot: eingebetteter Server und Proxy-Konfiguration](https://docs.spring.io/spring-boot/how-to/webserver.html),
[Spring Session: Cookie-Konfiguration](https://docs.spring.io/spring-session/reference/configuration/common.html),
[GitHub: Docker-Images veröffentlichen](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images),
[Temurin-Image](https://hub.docker.com/_/eclipse-temurin/).
