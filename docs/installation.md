# Ersteinrichtung und Datenbank-Upgrades

Diese Anleitung beschreibt den Stand von R04. Die vollständige Produktionsfreigabe
steht noch aus; insbesondere Produktionsprofil, Storage
und Releasebetrieb sind in der [Roadmap](roadmap.md) offen.

## Neue Installation ohne Demodaten

1. Eine eigene, leere MariaDB-Datenbank und die Datenbank-/Storage-Konfiguration
   bereitstellen. Für die Migration benötigt der Datenbankbenutzer auch DDL-Rechte.
   Die Trennung zwischen Migrations- und Laufzeitbenutzer folgt in R12.
2. Kein `development`-Profil aktivieren. Auch ohne explizites Spring-Profil werden
   ausschließlich Schema-Migrationen ausgeführt. Es werden keine Demo-Seiten,
   Demo-Gruppen oder Demo-Benutzer angelegt.
3. Für den ersten Start `KR_BOOTSTRAP_LOGIN` und `KR_BOOTSTRAP_PASSWORD` über die
   geschützte Laufzeitkonfiguration beziehungsweise einen Secret-Manager setzen.
   Der Login hat 3–100 Zeichen, beginnt mit einem ASCII-Buchstaben oder einer Ziffer
   und erlaubt danach zusätzlich `.`, `_`, `@` und `-`. Er wird kleingeschrieben.
   Das individuell gewählte Passwort muss 16–128 Zeichen lang sein; empfohlen ist
   ein vom Passwortmanager erzeugtes Passwort. Keine Zugangsdaten in Git oder
   in Kommandozeilenargumenten ablegen.
4. Die Anwendung mit `java -jar target/knowledgeroot-0.0.1-SNAPSHOT.jar` starten.
   Zunächst laufen die Migrationen, danach wird genau ein aktiver Administrator
   angelegt. Fehlende oder ungültige Bootstrap-Werte brechen den Start ab;
   ein teilweise angelegtes Konto bleibt dabei nicht zurück. Das migrierte Schema
   bleibt bestehen und kann beim nächsten Start wiederverwendet werden.
5. Mit den individuell gesetzten Zugangsdaten anmelden. Die beiden Bootstrap-Werte
   danach aus der Laufzeitkonfiguration entfernen. Ein erneuter Start benötigt sie
   nicht und verändert vorhandene Konten und Passwörter nicht.

Der Bootstrap ist ausschließlich für eine bisher unbenutzte Datenbank bestimmt.
Die Tabelle `knowledgeroot_setup` vermerkt den Abschluss. Eine Datenbanksperre
serialisiert gleichzeitig startende Instanzen. Auch nach dem späteren Entfernen
aller Konten wird der Bootstrap nicht wieder geöffnet. Die erste Benutzer-ID wird
von MariaDB vergeben; Auditfelder des neuen Administrators verweisen auf diese ID.

Seit R06 verwendet auch der neue Administrator PBKDF2-HMAC-SHA-256. Details zu
Passwortregeln, Hashmigration und Login-Drosselung stehen in [authentication.md](authentication.md).

## Demodaten für lokale Entwicklung

Nur mit einer separaten, entbehrlichen Entwicklungsdatenbank starten:

```sh
java -jar target/knowledgeroot-0.0.1-SNAPSHOT.jar --spring.profiles.active=development
```

Das Profil setzt `knowledgeroot.demo-data.enabled=true`; erst die Kombination aus
dieser Einstellung und dem Profil `development` erlaubt den Demo-Kontext.
Ein gleichzeitig aktives `production`-Profil wird abgelehnt. Mit
`--knowledgeroot.demo-data.enabled=false` lässt sich das Entwicklungsprofil auch
ohne Demodaten verwenden; dann gelten die normalen Bootstrap-Regeln.

Der Demo-Changeset verlangt auch bei direkter Liquibase-Ausführung einen expliziten
`development`-Kontext (`@development`). Ohne Kontextfilter werden keine Demodaten
geladen. Der erstmalige Demo-Import in eine Datenbank mit vorhandenen Benutzern
wird abgebrochen. Bereits ausgeführte Demo-Migrationen werden nicht wiederholt.
Die historischen Demo-SQL-Dateien bleiben unverändert.

### Start aus IntelliJ IDEA

Für eine lokale Demo-/Entwicklungsdatenbank in der Run-Konfiguration von
`KnowledgerootApplication` unter **Program arguments** ergänzen:

```text
--spring.profiles.active=development
```

Das Profil lädt `application-development.properties` und aktiviert dort den
Demo-Modus. Ein lokales Override von `knowledgeroot.demo-data.enabled=false`
verhindert diese Aktivierung weiterhin. Nicht gleichzeitig `production` aktivieren.

Die Meldung `Active demo credentials found` kommt aus der Startprüfung: Mindestens
ein aktives, nicht gelöschtes Konto verwendet noch den bekannten Demo-Passworthash,
während der Demo-Modus ausgeschaltet ist. Ein Start aus der IDE aktiviert diesen
Modus nicht automatisch. Für eine reguläre Installation die Konten wie im
folgenden Upgrade-Abschnitt bereinigen; zusätzliche Bootstrap-Zugangsdaten setzen
vorhandene Konten nicht zurück.

## Upgrade einer bestehenden Installation

1. Vor dem Upgrade Datenbank und Dateien sichern. Die neue Version zunächst an
   einer Kopie prüfen. Andere Anwendungsinstanzen während des Upgrades stoppen.
2. Noch unter der bisherigen Version vorhandene Demo-Konten prüfen, insbesondere
   `admin`, `user` und `guest`. Aktive Konten mit dem im Repository veröffentlichten
   Demo-Passworthash verhindern künftig den Start außerhalb des Demo-Modus.
   Benötigte Konten erhalten individuelle Passwörter; unbenötigte Konten werden
   deaktiviert. Mindestens ein aktiver Administrator muss erhalten bleiben.
   Dies in einer abgeschotteten Wartungsumgebung durchführen. Allein das Ändern
   des Loginnamens genügt nicht: Die Prüfung erfolgt anhand des Passwort-Hashes.
3. Das Entwicklungsprofil und etwaige Demo-Aktivierung aus der Zielkonfiguration
   entfernen. `spring.session.jdbc.initialize-schema=never` verwenden; dies ist
   jetzt der Standard. Lokale Overrides ebenfalls prüfen.
4. Die neue Version starten. Liquibase führt nur noch ausstehende Changesets aus.
   Bestehende Konten, Seiten und Berechtigungen werden nicht durch den Bootstrap
   ersetzt. Die bisherige Demo-Historie bleibt in `DATABASECHANGELOG` erhalten.
5. Anmeldung, Rollen, Seiten und Anhänge prüfen. Demo-Inhalte und Freigaben vor
   einer Veröffentlichung fachlich kontrollieren; die Migration löscht sie nicht.

Wenn der Start wegen Demo-Zugangsdaten oder eines fehlenden aktiven Administrators
abbricht, die vorbereitende Bereinigung in der abgeschotteten bisherigen Version
abschließen beziehungsweise ein geprüftes Backup mit funktionsfähigem Admin
wiederherstellen. Bootstrap-Variablen dienen nicht zum Zurücksetzen vorhandener
Konten. Die Setup-Markierung nicht löschen, um eine Wiederherstellung zu umgehen.

## JDBC-Sitzungsschema

Changeset `1.0.5-jdbc-session` verwaltet `SPRING_SESSION` und
`SPRING_SESSION_ATTRIBUTES`. Er verwendet das eingefrorene MySQL/MariaDB-Schema
von Spring Session JDBC 3.5.6. Bereits durch Spring Boot angelegte kompatible
Tabellen werden mit ihren Daten übernommen. Fehlende Tabellen werden angelegt;
inkompatible manuelle Schemaänderungen werden nicht automatisch repariert.
Weitere Schemaänderungen benötigen neue Changesets.

Die Spring-eigene Schemainitialisierung ist deaktiviert, damit ausschließlich
Liquibase das Schema verwaltet. Sitzungsspeicherung, Wiederlesen und kaskadierendes
Löschen werden gegen MariaDB getestet. Ein Upgrade-Test prüft außerdem die Übernahme
bestehender Sitzungen und unveränderte Checksummen der historischen Changesets.

## Automatisierte Prüfung

`InstallationTest` benötigt eine erreichbare Docker-Engine. Testcontainers startet
dafür eine temporäre MariaDB 12.2.2 mit isolierten Testdatenbanken und räumt den
Container anschließend auf. Die Tests lesen keine `.env`- oder lokalen
Anwendungskonfigurationen. Die Testcontainers-Version wird durch das vorhandene
Spring-Boot-BOM verwaltet.

```powershell
.\mvnw.cmd -B --no-transfer-progress verify
```

Unter Linux/macOS lautet der Wrapper-Aufruf `sh ./mvnw`. Ein Build ohne verfügbare
Docker-Engine schlägt fehl; die neuen Integrationstests werden nicht stillschweigend
übersprungen. R07 ergänzt einen Starttest des fertigen JARs gegen isolierte MariaDB-
und MinIO-Container samt HTTP-Login und JDBC-Sitzung über einen Neustart. Weitere
Storage-/Deploymenttests bleiben in R13; siehe [Abhängigkeiten und Prüfungen](dependencies.md).

Referenzen:

- [Liquibase-Kontexte und explizite Aktivierung](https://docs.liquibase.com/secure/reference-guide-5-1-1/changelog-attributes/what-are-contexts)
- [Liquibase-Checksummen und Metadatenänderungen](https://www.liquibase.com/blog/what-affects-changeset-checksums)
- [Spring Session JDBC: Schema und Serialisierung](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
