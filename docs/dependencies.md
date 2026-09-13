# Abhängigkeiten und Sicherheitsprüfung

Stand: 12. September 2026, Roadmap R08. Dieser Stand ist noch nicht zur Produktion freigegeben.

## Spring-Boot-Migration

Die Anwendung verwendet Spring Boot **4.1.1**, Java 25, Spring Security/Session 7.1.1/4.1.1,
Jackson 3.1.5 und Liquibase 5.0.3. Boot verwaltet die Versionen dieser Komponenten
und von Lombok. HTMXs Spring-/Thymeleaf-Integration verwendet die Boot-4-kompatible
Version 5.1.0. Die JavaScript-Bibliotheken und der Editor wurden in R08 bereinigt; siehe [editor.md](editor.md).

Einzige zusätzliche Spring-Stack-Versionsvorgabe ist `tomcat.version=11.0.25`:
Boot 4.1.1 verwaltet noch 11.0.24, für das inzwischen Sicherheitskorrekturen
veröffentlicht wurden. Die Vorgabe entfernen, sobald Boots BOM mindestens diesen
Stand verwaltet. Alle eingebetteten Tomcat-Module verwenden dieselbe Version.

Entfernt wurden Spring Session 1.3.5, ungenutztes JavaFaker samt Android-SnakeYAML,
redundante Logging-Abhängigkeiten und die alten WebJars-Locator-Varianten. Außerdem
entfallen der ungenutzte Mail-Starter und der PostgreSQL-Treiber; das tatsächlich
implementierte und getestete Datenbankschema ist MariaDB-spezifisch.
`webjars-locator-lite` übernimmt die versionslosen Ressourcenadressen.

Web MVC, Liquibase, JDBC-Sitzungen und die zugehörigen Tests verwenden die
modularen Boot-4-Starter. Tests verwenden Springs `@MockitoBean` und Jackson 3.
Jackson-Annotationen behalten den offiziellen `com.fasterxml.jackson.annotation`-Namensraum.
MinIO benötigt weiterhin Jackson 2 in dessen eigenem Namensraum; dies ist kein
zweites MVC-JSON-Backend. Legacy-Passwort- und Dateihashes verwenden JDK `HexFormat`
statt `javax.xml.bind`. Die Darstellung bleibt kompatibel: Legacy-Passworthashes
sind großgeschrieben, Dateihashes werden weiterhin kleingeschrieben gespeichert.

Das entfernte Boot-Plugin-Flag `executable` erzeugte ein eingebettetes Unix-Startskript.
Das JAR wird weiterhin mit `java -jar target/knowledgeroot-0.0.1-SNAPSHOT.jar` gestartet.
Milestone-Repositories entfallen; der Build verwendet veröffentlichte Artefakte aus Maven Central.
Die veraltete Property `spring.session.store-type` entfällt; der JDBC-Starter wählt
das Repository, Liquibase bleibt für dessen Schema zuständig.

## Sicherheitsprüfung wiederholen

Vom Repository-Verzeichnis aus mit PowerShell (Windows PowerShell oder PowerShell 7):

```powershell
.\mvnw.cmd -B --no-transfer-progress dependency:tree '-DoutputType=json' '-DoutputFile=target/dependency-tree.json'
.\scripts\audit-dependencies.ps1
```

Unter Linux/macOS den Maven-Befehl mit `sh ./mvnw` und das Skript mit `pwsh -File`
ausführen. Das Skript fragt OSV nach den aufgelösten Maven-Artefakten einschließlich
Testabhängigkeiten sowie nach den npm-Paketen der npm-WebJars und von jQuery,
Bootstrap und jsTree sowie nach den Paketen aus `frontend/package-lock.json`. Es übermittelt ausschließlich öffentliche Paketkoordinaten
und Versionen. Pagination wird berücksichtigt; Netzwerk-/Abfragefehler brechen ab.

`target/dependency-audit.json` enthält Zeitpunkt, Hashes von Dependency-Baum und Frontend-Lockfile,
Abfragen und vollständige Advisories. Exitcode 1 bedeutet vorhandene Funde oder
einen fehlgeschlagenen Lauf; nur ein vollständig erzeugter Bericht erlaubt eine
Bewertung. Funde werden nicht unterdrückt. Der Scan ist eine Prüfung bekannter
Versionsmeldungen, kein Nachweis der Ausnutzbarkeit oder vollständiger Sicherheit.
Nicht enthalten sind Container-Images, Maven-/CI-Plugins, zusätzliche CDN-Dateien
und die vollständige Analyse gebündelten JavaScripts. Diese Prüfungen bleiben
für die Release-Freigabe nötig.

## Ergebnis nach R08

Der Scan erfasst **267 Paketversionen** aus Maven und dem Frontend-Lockfile und
meldet **keine bekannten Advisories** (Exitcode 0). Die zehn Zuordnungen zu neun
Advisories aus R07 sind durch folgende Änderungen entfallen:

| Änderung | Erledigte Funde |
| --- | --- |
| HTML Sanitizer 20260313.1 | GHSA-g9gq-3pfx-2gw2; Regressionstests für ausführbares HTML und bestehende Formatierung ergänzt. |
| MinIO 9.0.3 mit Bouncy Castle 1.84 | GHSA-h7rh-xfpj-hpcm, GHSA-574f-3g2m-x479 und GHSA-c3fc-8qff-9hwx. Der Maven-Build benötigt ausdrücklich `okhttp-jvm` 5.3.2; der neue SDK-Aufruf verwendet `Long` für Streamgrößen. Echter Upload/Download geprüft. |
| CKEditor-Classic-Build und dessen WebJar-Abhängigkeiten entfernt | GHSA-rgg8-g5x8-wr9v, GHSA-jrqm-vmqc-gm93 sowie die drei lodash-es-Advisories GHSA-f23m-r3pf-42rh, GHSA-r5fr-rjxr-66jc und GHSA-xxjr-mmjv-4gpg. Tiptap 3.31.3 wird mit DOMPurify 3.4.15 aus dem Lockfile gebündelt und lokal ausgeliefert. |

Zusätzlich: Commons IO 2.22.0 und HTMX 2.0.10. Bootstrap 5.3.8 und Bootstrap Icons
1.13.1 wurden geprüft und beibehalten. HTMX bleibt auf der verwendeten 2.x-Linie;
ein Wechsel auf 4.x wäre eine eigene Schnittstellenmigration. Ungenutztes jQuery,
jsTree, Font Awesome, Mustache und Hyperscript entfallen. Die nicht vorhandene,
ungenutzte alte HTMX-Erweiterungsdatei wird nicht länger angefordert.

Der Scan unterdrückt weiterhin keine Funde. „Keine bekannten Advisories“ gilt
für die abgefragten Paketversionen zum Prüfzeitpunkt, nicht als allgemeine
Sicherheitsgarantie. Container, CI-Plugins und die Betriebs-/Releaseprüfungen aus
R12–R14 bleiben offen.

## Erneute Prüfung in R14

Am 13. September 2026 wurde der aufgelöste Abhängigkeitsbaum des Kandidaten
1.0.0-rc.1 einschließlich Actuator erneut gemeinsam mit dem Frontend-Lockfile
abgefragt: **277 Paketversionen, keine bekannten Advisories, Exitcode 0**.
Bericht: `target/dependency-audit.json`. Der GitHub-Workflow führt diesen Scan
jetzt vor jeder Veröffentlichung aus und archiviert den Bericht mit dem
Release-Nachweis. Container-OS-Pakete sind nicht Bestandteil dieser OSV-Abfrage.

Die folgenden Testgrenzen beschreiben den damaligen R08-Stand. Die inzwischen
aktiven Integrationstests, Container-/Ausfalltests und Restore-/Rollbackprüfung
sind in [monitoring.md](monitoring.md), [recovery.md](recovery.md) und der
[Roadmap](roadmap.md) festgehalten.

## Testgrenzen bei R08

Der vorhandene Testsatz prüft nach der Migration weiterhin Login, Rechte,
Passwortmigration, parallele Login-Quoten und Datenbank-Upgrades. Der neue
`ApplicationSmokeIT` startet das fertig gebaute JAR in separaten Java-Prozessen
mit echtem HTTP-Server und isolierten MariaDB-/MinIO-Containern. Failsafe führt
ihn bei `verify` nach dem Packaging aus. Er prüft Thymeleaf, versionslose WebJars,
Formularlogin mit CSRF, die Admin-API über JPA, Passwortausblendung und JDBC-Sitzungen
über einen Neustart ohne erneute Bootstrap-Zugangsdaten sowie Logout.

Der Test nutzt eine festgelegte ältere MinIO-Version als Kompatibilitätsfixture,
nicht als Empfehlung für den Produktionsbetrieb. Er umfasst auch die
Browser-/Upload-/Downloadprüfung aus R08. Vollständige Storage-Grenzfälle, weitere
Browser, Container- und Restoretests bleiben in R11–R14. Die zwei bereits
deaktivierten Integrationstestklassen bleiben R13.
Ein gemischter Betrieb alter und neuer Anwendungsversionen ist nicht nachgewiesen.

## Quellen

- [Spring Boot 4.1.1 Release](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/)
- [Offizieller Boot-4-Migrationsleitfaden](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [HTMX-Kompatibilität](https://github.com/wimdeblauwe/htmx-spring-boot#spring-boot-compatibility)
- [Tomcat-11-Sicherheitskorrekturen](https://tomcat.apache.org/security-11.html)
- [OSV-Abfragen und Pagination](https://google.github.io/osv.dev/post-v1-querybatch/)
