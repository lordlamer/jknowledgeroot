# Backup, Wiederherstellung und Upgrade

Stand: R19, 13. September 2026. Die Skripte gelten für **eine Instanz mit lokalem
Dateispeicher** im unveränderten [Produktions-Compose](../deploy/compose.production.yaml).
MariaDB und sämtliche Dateien bilden gemeinsam einen Wiederherstellungspunkt.
Ein SQL-Dump allein reicht nicht. Für externes S3 sind zusätzlich ein konsistenter
Bucket-Snapshot und eine eigene Restore-Abnahme notwendig; dieser Weg wird vom
Skript nicht unterstützt.

## Voraussetzungen und Sicherungsumfang

Bash, Docker Compose, `tar`, `sha256sum` und ausreichend freier Platz müssen
vorhanden sein. Unter Windows Git Bash verwenden; binäre Tar-Dateien nicht durch
die Textumleitung von Windows PowerShell 5 leiten. Die Datenbankwerkzeuge laufen
im vorhandenen MariaDB-Container. Ein eigener Projektname und eine explizite
Env-Datei sind immer erforderlich. Die Skripte übernehmen keine gleichnamigen
`KR_*`-Overrides aus der aufrufenden Shell.

Die Sicherung enthält `database.sql`, `files.tar.gz`, `manifest.txt` und
`SHA256SUMS`. Der Dump enthält alle Anwendungstabellen einschließlich Konten,
Passworthashes, Rechte, Sitzungen, Liquibase-Verlauf und Dateireferenzen. Das
Dateiarchiv enthält das gesamte Volume, auch noch referenzierte gelöschte Anhänge
und etwaige temporäre Dateien. Die Metadaten nennen Zeitpunkt, Datenbankversion
und die tatsächlichen Image-IDs der gesicherten Container. Die Betriebssystem-
und Datenbankkonten werden nicht exportiert; sie entstehen im neuen Ziel durch
das Initialisierungsskript und dessen eigene Zugangsdaten.

Zusätzlich gesondert und geschützt aufbewahren: passende Compose-/Skriptversion,
Konfiguration und Secrets, TLS-/Proxy-Konfiguration sowie das alte Anwendungsimage
und seine Release-Metadaten. Ein Image-Name allein garantiert nicht, dass das Image
später noch verfügbar ist. Beispielsweise mit `docker save` archivieren oder einen
unveränderlichen Registry-Digest mit abgesicherter Aufbewahrung verwenden.

Das Backup enthält vertrauliche Daten und aktive Sitzungen. `umask 077` begrenzt
unter Unix neue Dateirechte; auf Windows die NTFS-Zugriffsrechte ausdrücklich
prüfen. Sicherungen verschlüsselt und außerhalb des Anwendungsservers speichern,
Zugriff beschränken und regelmäßig in einer getrennten Umgebung testen. SHA-256
erkennt Beschädigungen, authentifiziert aber keine Sicherung. Nur eigene,
vertrauenswürdige Backups importieren: SQL ist ausführbarer Inhalt.

## Konsistent sichern

1. Wartungsmodus am Proxy aktivieren, alle Anwendungsinstanzen und sonstigen
   Writer auf diese Datenbank und diesen Speicher stoppen. Es darf auch kein
   Upgrade oder Storage-Cleanup parallel laufen. Das Skript prüft die
   App-Container seines Projekts; externe Writer kann es nicht erkennen.
2. Anwendung regulär stoppen, Datenbank laufen lassen:

   ```sh
   docker compose --project-name knowledgeroot-prod --env-file /secure/prod.env -f deploy/compose.production.yaml stop app
   bash deploy/recovery.sh backup knowledgeroot-prod /secure/prod.env /secure/backups/pre-upgrade-2026-09-13
   ```

3. Der Zielordner muss neu sein. Der Dump verwendet eine Transaktion und erfasst
   Daten binärsicher; der gestoppte Writer hält Datenbank und Dateiarchiv auf
   demselben fachlichen Stand. `INCOMPLETE` bleibt bei Fehlern bestehen. Solche
   Sicherungen gelten nicht als erfolgreich. Keine Dateien einzeln zwischen
   verschiedenen Sicherungen austauschen.
4. Erfolg prüfen, Sicherung geschützt auslagern und einen Restore testen. Danach
   entweder kontrolliert upgraden oder die bisherige Anwendung starten. Das
   Skript startet sie auch nach einem Fehler nicht automatisch.

## In eine leere Umgebung wiederherstellen

Eine getrennte Env-Datei mit passendem Image, eigenen Datenbankzugangsdaten und
einem freien Loopback-Port vorbereiten. Bootstrap-Werte leer lassen: Das Backup
enthält bereits den Administrator. Den neuen Projektnamen und die neuen Volumes
vor dem Start prüfen. Für eine Wiederherstellung ohne Datenbankupgrade
`KR_DB_IMAGE` ausdrücklich auf die gesicherte Version setzen; ein leeres Feld
ist nicht zulässig. `manifest.txt` nennt
`database_image_ref`, `database_image_id` und `database_version`; ältere Backups
enthalten nur die beiden letzten Angaben. Das alte Image zusätzlich archivieren,
da eine lokale Image-ID allein keinen erneuten Registry-Download ermöglicht.

```sh
docker compose --project-name knowledgeroot-restored --env-file /secure/restored.env -f deploy/compose.production.yaml up -d --wait database
bash deploy/recovery.sh restore knowledgeroot-restored /secure/restored.env /secure/backups/pre-upgrade-2026-09-13
docker compose --project-name knowledgeroot-restored --env-file /secure/restored.env -f deploy/compose.production.yaml up -d --wait app
```

Die Anwendung vor dem Import nicht starten: Schon der erste Start migriert die
Datenbank und macht sie für diesen Restore ungeeignet. Das Skript verweigert
laufende App-Container, beschädigte/unvollständige Sicherungen sowie eine
Zieldatenbank mit Tabellen oder ein nicht leeres Datei-Volume. Es löscht keine
Bestandsdaten. Das Archiv wird mit der App-UID 10001 eingespielt.

Ein abgebrochener Import ist nicht atomar über SQL und Dateien hinweg. Das Ziel
bleibt gestoppt und muss untersucht werden. Einen neuen leeren Zielstand verwenden;
das Skript überschreibt oder bereinigt den teilweise importierten Stand nicht.
Vor dem Öffnen des Proxys prüfen:

- Readiness, Startlogs und Liquibase-Status ohne unerwartete Migrationen.
- Anmeldung vorhandener Konten sowie erlaubte und verbotene Seitenzugriffe,
  einschließlich Gruppenrechten und Vererbung.
- Inhalte, Labels, Kommentare und Sterne; Anhänge herunterladen und gegen bekannte
  Prüfsummen vergleichen. Bei der Betriebsabnahme sämtliche Dateireferenzen mit
  dem gesicherten Objektbestand abgleichen, nicht nur Stichproben verwenden.
- Sitzungen aus dem Backup bei einem Sicherheitsvorfall vor der Freigabe verwerfen
  und betroffene Zugangsdaten separat rotieren.

Erst danach den Proxy auf das geprüfte Ziel umschalten. Den alten Stand bis zur
Abnahme behalten. Für reguläres Stoppen niemals `down --volumes` verwenden.

## Upgrade und Rückkehr

Vor jedem Upgrade alte Artefakte/Versionen dokumentieren, Writer stoppen und ein
gemeinsames Backup erstellen. Das neue Image zunächst auf einer wiederhergestellten
Kopie erproben. Liquibase migriert beim Start; automatische Änderungen an alten
Changesets und gemischter Schreibbetrieb verschiedener Versionen sind ausgeschlossen.

Der dokumentierte Rollback ist **Wiederherstellung des Vor-Upgrade-Backups mit dem
alten Image und dessen Konfiguration in einer leeren Umgebung**. Ein bloßer Wechsel
auf das alte JAR genügt nicht: Ältere Versionen verstehen beispielsweise neue
Passworthashes oder Dateischlüssel unter Umständen nicht. Es gibt keinen allgemein
geprüften Liquibase-Downgrade. Änderungen nach dem Sicherungszeitpunkt gehen bei
diesem Rückweg verloren; bis zur Freigabe deshalb keine produktiven Schreibzugriffe
zulassen. Spätere Datenübernahme verlangt eine eigene fachliche Abstimmung.

## Wechsel von MariaDB 12.2.2 auf 12.3.3

Der geprüfte Weg verwendet einen logischen Dump ausschließlich der
Anwendungsdatenbank. Die MariaDB-Systemtabellen und Datenbankkonten werden im
neuen Container frisch angelegt; alte `/var/lib/mysql`-Dateien werden nicht
übernommen. Vor Änderungen am bisherigen Compose-Projekt in dessen Env-Datei
die alte Datenbank festlegen:

```dotenv
KR_DB_IMAGE=mariadb:12.2.2@sha256:e16f61b8f6ed25111adbb1c5c19bbc2904efc8ed14029999af0cbe1c7ae18bf1
```

Writer stoppen und mit den obigen Befehlen einen gemeinsamen Snapshot erstellen.
Für das neue Projekt eigene leere Volumes, Zugangsdaten und einen freien Port
verwenden. Dort `KR_DB_IMAGE` auf das gemeinsam mit dem neuen App-Image geprüfte
12.3.3-Datenbankimage setzen. Zuerst nur die Datenbank starten, dann den Snapshot importieren und
erst danach das neue App-Image starten und abnehmen. Den bisherigen Stand bis
zur Freigabe behalten. Externe Datenbankkonten, Plugins und eigene
Serverkonfigurationen benötigen eine zusätzliche Prüfung.

Beim Rückweg den Snapshot **von vor dem Upgrade** mit altem App-Image und
explizitem 12.2.2-Image in eine weitere leere Umgebung importieren. Eine
12.3-Datenbank niemals mit 12.2-Binärdateien auf demselben Volume öffnen.

## Wiederholbarer Test und Grenzen

```sh
bash deploy/build-recovery-baseline.sh
NODE_BINARY="$PWD/target/frontend/node/node" bash deploy/recovery-smoke-test.sh knowledgeroot:verified knowledgeroot:recovery-baseline knowledgeroot:database-verified
```

Der Baseline-Build verwendet den festen R13-Commit
`7b85fa137c44b282441b47eb3d1d1f8c3f629e4d` in einem neuen Verzeichnis unter `target/`.
Er baut das vorherige Image, ohne den aktuellen Checkout umzuschalten. Die damaligen
Tests sind im R13-Nachweis festgehalten; der Baseline-Build selbst überspringt sie.
Der Restoretest legt drei eigene Compose-Projekte an und prüft vorhandene Konten,
öffentliche/private Seiten, Gruppenrechte, Kommentare, Labels, Sterne und
bytegleichen Dateidownload. Er stellt den R13-Stand mit dem neuen Image wieder her
und kehrt anschließend mit dem alten Image zum Vor-Upgrade-Snapshot zurück. Eine
absichtliche Änderung nach dem Upgrade ist nach diesem Rollback verschwunden.

Fehlerfälle prüfen laufende Writer, beschädigte Backups und nicht leere Ziele.
Die Testprojekte und ihre Volumes werden am Ende entfernt. Synthetische Backups
bleiben im ausgegebenen temporären Verzeichnis für die Fehleranalyse; Env-Dateien
mit Datenbankpasswörtern werden entfernt. Die HTTP-Prüfung arbeitet ausschließlich
auf Loopback und führt Cookies im Testclient selbst; die produktive TLS-/Cookie-
Prüfung bleibt Bestandteil des separaten JAR-Tests.

Die Referenzprüfung betrifft R13 mit MariaDB 12.2.2 auf den aktuellen Kandidaten
mit MariaDB 12.3.3 und zurück zum R13-Snapshot mit 12.2.2. Der Test prüft die
tatsächlich gestartete Serverversion in allen drei Umgebungen ausdrücklich.
Historische Schemamigrationen prüfen die bestehenden Java-Tests auf 12.3.3.
R14 selbst fügte keine neue Schemamigration hinzu. Der aktuelle R15-Kandidat
enthält `1.0.11-page-revisions`; die Referenzkette prüft damit auch das Upgrade
auf die neue Historienstruktur. Ein Rückweg verwendet weiterhin das alte Image
zusammen mit dem Vor-Upgrade-Snapshot, nicht die bereits migrierte Datenbank.
Das ist kein Nachweis für beliebige frühere Releases, Datenbank-Downgrades,
produktive Datenmengen oder S3-Restore. RPO (maximaler Datenverlust), RTO
(Wiederanlaufzeit), Aufbewahrung und der regelmäßige Restore-Termin sind je Betrieb
festzulegen und mit dessen Datenmenge zu messen.

Referenz: [MariaDB: mariadb-dump und Wiederherstellung](https://mariadb.com/docs/server/clients-and-utilities/backup-restore-and-import-clients/mariadb-dump).
