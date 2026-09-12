# Abhängigkeiten und Sicherheitsprüfung

Stand: 12. September 2026, Roadmap R07. Dieser Stand ist noch nicht zur Produktion freigegeben.

## Spring-Boot-Migration

Die Anwendung verwendet Spring Boot **4.1.1**, Java 25, Spring Security/Session 7.1.1/4.1.1,
Jackson 3.1.5 und Liquibase 5.0.3. Boot verwaltet die Versionen dieser Komponenten
und von Lombok. HTMXs Spring-/Thymeleaf-Integration verwendet die Boot-4-kompatible
Version 5.1.0. Die JavaScript-Bibliotheken werden gesondert in R08 bearbeitet.

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
Bootstrap und jsTree. Es übermittelt ausschließlich öffentliche Paketkoordinaten
und Versionen. Pagination wird berücksichtigt; Netzwerk-/Abfragefehler brechen ab.

`target/dependency-audit.json` enthält Zeitpunkt, Hash des Dependency-Baums,
Abfragen und vollständige Advisories. Exitcode 1 bedeutet vorhandene Funde oder
einen fehlgeschlagenen Lauf; nur ein vollständig erzeugter Bericht erlaubt eine
Bewertung. Funde werden nicht unterdrückt. Der Scan ist eine Prüfung bekannter
Versionsmeldungen, kein Nachweis der Ausnutzbarkeit oder vollständiger Sicherheit.
Nicht enthalten sind Container-Images, Maven-/CI-Plugins, zusätzliche CDN-Dateien
und die vollständige Analyse gebündelten JavaScripts. Diese Prüfungen bleiben
für die Release-Freigabe nötig.

## Bewertung der verbleibenden Funde

Nach dem Tomcat-Update verbleiben folgende Paket-/Advisory-Zuordnungen. Die
OSV-Prüfung erfasst 303 Paketversionen und meldet zehn Zuordnungen zu neun
unterschiedlichen Advisories; der Scan liefert deshalb weiterhin Exitcode 1.
Die Bewertung begründet die getrennte Bearbeitung in R08 und ist keine Freigabe zur
unbegrenzten Weiterverwendung. Vor dem Release erneut prüfen und aktualisieren.

| Paket | Advisory | Bewertung und nächster Schritt |
| --- | --- | --- |
| HTML Sanitizer 20240325.1 | [GHSA-g9gq-3pfx-2gw2](https://github.com/OWASP/java-html-sanitizer/security/advisories/GHSA-g9gq-3pfx-2gw2) | Die aktuelle Policy aktiviert nicht die beschriebene Kombination aus `noscript`, `style` und `allowTextIn("style")`. Update samt Regressionstests bleibt R08. |
| MinIO SDK 8.5.17 | [GHSA-h7rh-xfpj-hpcm](https://github.com/minio/minio-java/security/advisories/GHSA-h7rh-xfpj-hpcm) | Bösartige XML-Antworten können System-/Umgebungswerte substituieren. Der konfigurierte S3-Endpunkt ist die Eingabequelle; das ist keine belastbare allgemeine Entschärfung. In R08 mindestens 8.6.0 einsetzen und Storage prüfen. |
| Bouncy Castle 1.78.1 über MinIO | [GOST](https://github.com/advisories/GHSA-574f-3g2m-x479), [LDAP](https://github.com/advisories/GHSA-c3fc-8qff-9hwx) | Im Anwendungscode werden weder GOST-CTR noch LDAP-Zertifikatsabfragen verwendet. Keine vollständige transitive Erreichbarkeitsanalyse; beim SDK-Update in R08 auf einen korrigierten Stand, für beide Funde mindestens 1.84, bringen. |
| CKEditor Clipboard 40.1.0 und CKEditor 40.1.0 | [GHSA-rgg8-g5x8-wr9v](https://github.com/ckeditor/ckeditor5/security/advisories/GHSA-rgg8-g5x8-wr9v) | Zwei Paketmeldungen desselben Problems. Das Advisory benötigt Block Toolbar plus unsichere HTML-Unterstützung/HTML Embed. Die Anwendung konfiguriert keinen Block Toolbar; die tatsächlich enthaltenen Plugins sind in R08 im Browser zu prüfen. |
| CKEditor 40.1.0 | [GHSA-jrqm-vmqc-gm93](https://github.com/ckeditor/ckeditor5/security/advisories/GHSA-jrqm-vmqc-gm93) | Die Templates konfigurieren sehr freizügiges `htmlSupport` und HTML-Vorschauen. Ob der alte Classic-Build diese Plugins enthält, ist noch nicht praktisch geprüft. Keine Entwarnung aufgrund der serverseitigen Sanitization: clientseitige XSS muss R08 ausdrücklich abdecken. Korrigierter Stand laut Advisory mindestens 47.6.0. |
| lodash-es 4.17.21 über CKEditor | [Array-Pfade](https://github.com/lodash/lodash/security/advisories/GHSA-f23m-r3pf-42rh), [Template-Imports](https://github.com/lodash/lodash/security/advisories/GHSA-r5fr-rjxr-66jc), [Prototype Pollution](https://github.com/lodash/lodash/security/advisories/GHSA-xxjr-mmjv-4gpg) | Anwendungscode ruft die betroffenen Funktionen nicht direkt auf; der Editor-Bundle ist damit nicht als sicher nachgewiesen. Im Zuge der Editor-Migration mindestens 4.18.0 beziehungsweise einen bereinigten Bundle verwenden. Ein Maven-Override allein ersetzt keinen bereits gebündelten JavaScript-Code. |

## Testgrenzen

Der vorhandene Testsatz prüft nach der Migration weiterhin Login, Rechte,
Passwortmigration, parallele Login-Quoten und Datenbank-Upgrades. Der neue
`ApplicationSmokeIT` startet das fertig gebaute JAR in separaten Java-Prozessen
mit echtem HTTP-Server und isolierten MariaDB-/MinIO-Containern. Failsafe führt
ihn bei `verify` nach dem Packaging aus. Er prüft Thymeleaf, versionslose WebJars,
Formularlogin mit CSRF, die Admin-API über JPA, Passwortausblendung und JDBC-Sitzungen
über einen Neustart ohne erneute Bootstrap-Zugangsdaten sowie Logout.

Der Test nutzt eine festgelegte ältere MinIO-Version als Kompatibilitätsfixture,
nicht als Empfehlung für den Produktionsbetrieb. Er ersetzt weder die vollständigen
Upload-/Downloadfälle aus R08/R11 noch den Browser-, Container- und Restoretest aus
R12–R14. Die zwei bereits deaktivierten Integrationstestklassen bleiben R13.
Ein gemischter Betrieb alter und neuer Anwendungsversionen ist nicht nachgewiesen.

## Quellen

- [Spring Boot 4.1.1 Release](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/)
- [Offizieller Boot-4-Migrationsleitfaden](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [HTMX-Kompatibilität](https://github.com/wimdeblauwe/htmx-spring-boot#spring-boot-compatibility)
- [Tomcat-11-Sicherheitskorrekturen](https://tomcat.apache.org/security-11.html)
- [OSV-Abfragen und Pagination](https://google.github.io/osv.dev/post-v1-querybatch/)
