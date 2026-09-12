# Roadmap zur Produktionsreife

Stand: 12. September 2026. Grundlage ist die Code- und Build-Prüfung dieses Tages.

Ziel ist eine sicher betreibbare, reproduzierbar gebaute Version mit getesteten
Zugriffsrechten, Migrationen und Wiederherstellung. Der aktuelle Stand ist noch
nicht zur Produktion freigegeben.

## Arbeitsweise und Status

- Die Punkte werden in der folgenden Reihenfolge in überschaubaren Änderungen bearbeitet.
- Jeder Punkt bekommt passende Prüfungen; erledigt ist er erst, wenn seine Abnahmekriterien erfüllt sind.
- Nach jeder Umsetzung werden hier Status, Prüfergebnis und verbleibende Einschränkungen dokumentiert.
- Dependency-Updates erfolgen getrennt von fachlichen Änderungen, soweit sinnvoll. Zielversionen und Supportstatus werden bei der Umsetzung erneut geprüft.
- Änderungen an bereits angewendeten Datenbankschemata erfolgen über neue Migrationen.
- Offene Produktentscheidungen werden vor der davon abhängigen Implementierung geklärt. Unabhängige Arbeiten können weitergehen.

**Nächster Schritt: R07 – POM bereinigen und Spring aktualisieren.**

R01 bis R06 sind abgeschlossen. R07 bis R15 sind offen; die Produktionsfreigabe steht weiterhin aus.

## 1. Build und Sicherheit

### R01 – Verlässlichen Build herstellen

- [x] Erledigt am 12. September 2026
- **Befund:** `PageControllerTest` verwendet einen veralteten Konstruktor. `mvn -B test` scheitert bei der Testkompilierung. CI verwendet Java 21, das Projekt Java 25; der Wrapper verwendet Maven 3.5.2.
- **Umsetzung:** Testaufbau an Sterne, Kommentare und Labels anpassen; vorhandene Testfehler beheben; Java-Versionen in POM, CI und Container abstimmen; Maven-Wrapper aktualisieren und für CI verwenden.
- **Abnahme:** Wrapper-Build mit `verify` läuft auf der gewählten Java-Version erfolgreich. Kein aktiver Test wird zur Umgehung eines Fehlers deaktiviert. Die zwei bereits deaktivierten Integrationstestklassen bleiben als offene Arbeit in R13 sichtbar.
- **Umgesetzt:** Testkonstruktor um die drei fehlenden DAO-Mocks ergänzt. GitHub Actions und vorhandene Travis-Konfiguration auf Java 25 und Wrapper-`verify` umgestellt; POM und Container verwenden bereits Java 25. Maven Wrapper 3.3.4 als `only-script` für Maven 3.9.16 neu erzeugt; altes Wrapper-JAR entfernt. Download mit SHA-256 abgesichert, Zeilenenden der Wrapper-Skripte in `.gitattributes` festgelegt und Build-/Startbefehle im README dokumentiert.
- **Geprüft:** `.\mvnw.cmd -B --no-transfer-progress clean verify` unter Windows mit Temurin JDK 25 und Maven 3.9.16: **BUILD SUCCESS**. 27 Tests erfasst, davon **17 erfolgreich, 10 bereits zuvor deaktiviert**, keine Fehler. Ausführbares JAR erzeugt. Shell-Wrapper mit `bash -c 'sh ./mvnw --version'` unter Git Bash geprüft; Maven 3.9.16 und Java 25 bestätigt. Maven-Archiv vor Übernahme des SHA-256-Wertes gegen die veröffentlichte SHA-512-Prüfsumme geprüft. `git diff --check` ohne Fehler.
- **Verbleibende Einschränkungen:** Gehostete CI und Linux-Container wurden nicht ausgeführt. Die 10 übersprungenen Tests gehören zu den zwei bekannten Integrationstestklassen in R13. Bestehende Deprecation-/JVM-Warnungen bleiben für die Dependency-Arbeiten sichtbar; ein echter Datenbank-/Storage-Start ist mit R01 nicht abgedeckt.

### R02 – Seitenrechte in der REST-API durchsetzen

- [x] Erledigt am 12. September 2026
- **Befund:** `GET /page` und `GET /page/{id}` prüfen keine individuellen Seitenrechte. Die Rollenprüfung erlaubt normalen Benutzern den Zugriff auf diese Endpunkte.
- **Umsetzung:** Berechtigungen für Listen und Einzelabrufe konsistent mit der Oberfläche prüfen; weitere Zugriffswege einschließlich Dateien, Suche und Hierarchie auf dieselbe Regel prüfen.
- **Abnahme:** Benutzer ohne Leserecht erhalten weder Inhalt noch vertrauliche Metadaten geschützter Seiten. Tests decken Gast, normalen Benutzer, Gruppenmitglied und Administrator sowie fremde Seiten-IDs ab.
- **Umgesetzt:** REST-Listen filtern Seiten über den gemeinsamen `PagePermissionDao`. Einzelabrufe und die ID-Abfrage der Liste verweigern unberechtigten Zugriff mit 403 vor dem Laden der Inhalte; fehlende Datensätze werden bei erlaubtem Zugriff mit 404 beantwortet. Lesbare Unterseiten geben die ID geschützter Eltern in der API als `null` zurück; die Brotkrümelnavigation enthält nur lesbare Vorfahren. Auch das Löschen eines Kommentars verlangt jetzt Leserecht auf die Seite, bevor Kommentare geladen oder geändert werden. Damit kann eine beliebige, nicht vorhandene Kommentar-ID keine fremde Kommentarliste mehr offenlegen.
- **Weitere Zugriffswege geprüft:** Dateien prüfen bereits das Leserecht auf ihre tatsächliche Seite, einschließlich Metadaten und beider REST-Downloadadressen. Suche und Seitenleiste filtern nach Leserechten; gemerkte Seiten werden in `PageStarImpl.listStarredPages` nach aktuellen Rechten gefiltert. Vorhandene Filter beibehalten und Datei-/Seitenleisten-Regressionsfälle ergänzt.
- **Geprüft:** `.\mvnw.cmd -B --no-transfer-progress verify`: **BUILD SUCCESS**, 55 Tests erfasst, davon **45 erfolgreich und 10 bereits zuvor deaktiviert**, keine Fehler. 28 neue Testfälle decken REST-Filter und direkte Seiten-IDs, Weitergabe der Benutzer-ID an die Berechtigungsprüfung, Gastzugriff, erlaubte Benutzer-/Gruppen-/Admin-Szenarien, Elternmetadaten, Kommentarzugriff, Seitenleiste und Dateien ab. `git diff --check` ohne Fehler.
- **Testgrenzen:** Die REST-Tests verwenden echte Controller und die Spring-Security-Filterkette mit simuliertem Benutzerkontext und gemockten DAOs. Sie prüfen die Durchsetzung der Berechtigungsentscheidung; Gruppenmitgliedschafts-SQL, echter Login und Sitzungspersistenz werden damit nicht integriert getestet und bleiben in R03/R13. Die REST-Liste filtert vor der Ausgabe, aber noch nach der Datenbank-Pagination; dadurch können Ergebnisseiten kürzer ausfallen. Die Optimierung einschließlich Pagination bleibt in R10.

### R03 – Echten Login und Sitzungen korrigieren

- [x] Erledigt am 12. September 2026
- **Befund:** Das vom AuthenticationProvider erzeugte `KnowledgerootUserToken` meldet `isAuthenticated=false`; ein isolierter Laufzeittest bestätigt dies. Tests mit `@WithMockUser` prüfen diesen Login-Pfad nicht.
- **Umsetzung:** Authentifizierungsstatus korrekt setzen; Login, geschützte Folgerequests und Logout mit echten Anwendungstokens testen; Verhalten bestehender Sitzungen bei Kontosperrung und Rollenentzug festlegen und durchsetzen.
- **Abnahme:** Erfolgreicher Login erlaubt berechtigte Folgerequests, fehlgeschlagener Login keine. Logout beendet die Sitzung. Kontosperrung und Rechteentzug wirken gemäß dokumentierter Regel.
- **Umgesetzt:** Der Provider erzeugt nach erfolgreicher Passwort- und Kontoprüfung ein authentifiziertes Anwendungstoken; dessen Rollen stammen aus dem geladenen Benutzerkonto. Ungültige Zugangsdaten und fehlerhafte Legacy-Hashformate führen zur kontrollierten Login-Ablehnung. Login und Kontosuche normalisieren den Namen mit `Locale.ROOT`. Ein nicht authentifiziertes Token liefert keinen angemeldeten Benutzerkontext mehr.
- **Sitzungsregel:** Vor der Autorisierung eines Folgerequests wird das aktive, nicht gelöschte Konto anhand seiner unveränderlichen Benutzer-ID neu geladen. Sperrung, Löschung und jede Änderung zwischen USER und ADMIN beenden die jeweilige Sitzung bei ihrem nächsten Request; anschließend ist eine neue Anmeldung nötig. Eine Umbenennung aktualisiert den Sitzungskontext unter Beibehaltung der Benutzer-ID. Datenbankfehler brechen den Request vor dem Anwendungsendpunkt ab. Bereits laufende Requests werden nicht nachträglich abgebrochen. Logout beendet die aktuelle Sitzung.
- **Persistenz:** Login und Sitzungsprüfung verwenden ausdrücklich dasselbe SecurityContextRepository. Aktualisierte Kontexte werden gespeichert, ohne das Kontextobjekt paralleler Requests zu verändern. Die bisher berechnete Serialisierungskennung des Anwendungstokens ist explizit festgeschrieben; alte, nicht authentifizierte Tokens werden bei erneuter Verwendung abgemeldet. Die Betriebsregeln sind auch im README dokumentiert.
- **Geprüft:** `.\mvnw.cmd -B --no-transfer-progress verify`: **BUILD SUCCESS**, 75 Tests erfasst, davon **65 erfolgreich und 10 bereits zuvor deaktiviert**, keine Fehler. 20 neue Fälle prüfen echten Formularlogin, geschützte Folgerequests, Fehlversuche, defekte Hashformate, CSRF bei Login und Logout, Wechsel der Session-ID beim Login, Logout, mehrere Sitzungen eines gesperrten Kontos, beide Richtungen einer Rollenänderung, Umbenennung mit Wiedervergabe des alten Logins, Serialisierung und Wiederherstellung des SecurityContext, alte Tokens, Datenbankausfall und serverunabhängige Namensnormalisierung. `git diff --check` ohne Fehler.
- **Testgrenzen:** Die neuen Tests verwenden den echten Provider, Benutzerkontext und die Security-Filterkette mit `MockHttpSession`; Datenbankzugriffe sind gemockt. SQL gegen MariaDB, JDBC-Sitzungspersistenz und ein Deployment mit mehreren Instanzen bleiben in R13. Pro authentifiziertem Request fällt eine Kontodatenbankabfrage an. Modernisierung der Passwortspeicherung und Login-Drosselung bleiben in R06; die alten Spring-Session-Abhängigkeiten werden in R07 bereinigt.

### R04 – Sichere Ersteinrichtung und Migrationen

- [x] Erledigt am 12. September 2026
- **Befund:** Liquibase aktiviert fest `development, production`; frische Installationen erhalten dadurch Demodaten einschließlich eines Admin-Kontos mit festem Passwort-Hash.
- **Umsetzung:** Demodaten ausdrücklich auf Entwicklung begrenzen; sichere Einrichtung des ersten Administrators vorsehen; Umgang mit vorhandenen Demo-Konten dokumentieren. Session-Schema über einen definierten Migrationsprozess verwalten.
- **Abnahme:** Eine frische Produktionsdatenbank enthält keine Demo-Konten oder Demo-Inhalte. Erstzugang benötigt individuell gesetzte Zugangsdaten. Wiederholter Start und Upgrade einer bestehenden Datenbank funktionieren ohne Datenverlust.
- **Umgesetzt:** Liquibase verwendet standardmäßig ausschließlich den Schema-Kontext `production`. Demodaten erfordern ausdrücklich das Entwicklungsprofil und die Demo-Einstellung; eine gleichzeitige Aktivierung des Produktionsprofils wird abgelehnt. Der historische Demo-Changeset verlangt jetzt `@development` und verweigert den erstmaligen Import in eine Datenbank mit vorhandenen Benutzern. Die historischen SQL-Dateien bleiben unverändert.
- **Erstzugang:** Nach den Migrationen legt der Bootstrap in einer noch unbenutzten Datenbank genau einen aktiven Administrator aus `KR_BOOTSTRAP_LOGIN` und `KR_BOOTSTRAP_PASSWORD` an. Fehlende oder ungültige Werte verhindern den Start. Kontoerstellung, Auditfelder und Setup-Markierung sind transaktional; eine gesperrte Setup-Zeile serialisiert parallele Instanzen. Vorhandene Konten werden nie überschrieben. Nach abgeschlossener Einrichtung werden keine Bootstrap-Zugangsdaten mehr benötigt; auch das spätere Entfernen aller Konten öffnet die Einrichtung nicht erneut.
- **Upgrade und Sitzungen:** Aktive Konten mit dem bekannten Demo-Passworthash verhindern einen Start außerhalb des Demo-Modus; die Bereinigung und Wiederherstellung sind in [installation.md](installation.md) dokumentiert. Eine bestehende Installation ohne aktiven Administrator wird ebenfalls abgelehnt. Neue Changesets `1.0.5` und `1.0.6` verwalten Session-Schema und Setup-Markierung. Kompatible, zuvor von Spring Boot angelegte Session-Tabellen werden samt Daten übernommen; Springs eigene Schemainitialisierung ist deaktiviert.
- **Geprüft:** `.\mvnw.cmd -B --no-transfer-progress verify`: **BUILD SUCCESS**, 90 Tests erfasst, davon **80 erfolgreich und 10 bereits zuvor deaktiviert**, keine Fehler. 15 neue Tests verwenden eine isolierte MariaDB 12.2.2 über Testcontainers. Geprüft sind Neuinstallation, fehlende/ungültige Zugangsdaten, Spring-Initialisierungsreihenfolge, wiederholter Start, konkurrierender Bootstrap, Rollback nach einem INSERT, deaktivierte/entfernte Administratoren, Demo-Profilregeln und Upgrade anhand der historischen Changesets. Konten, Inhalte und Changeset-Checksummen bleiben beim Upgrade erhalten; JDBC-Sitzungen lassen sich speichern, wiederlesen, übernehmen und kaskadierend löschen. `git diff --check` ohne Fehler.
- **Testgrenzen und Betrieb:** Der Wrapper-Build benötigt jetzt Docker; Testcontainers-Abhängigkeiten verwenden die vom bestehenden Boot-BOM verwaltete Version. Die privaten lokalen Konfigurationen und bestehende Dienste wurden nicht verändert. Die Tests starten die Migrations-/Bootstrap-Konfiguration und das JDBC-Session-Repository, aber noch keine vollständige Anwendung mit HTTP und Storage. Diese breitere Integration bleibt in R13. Das Legacy-Hashverfahren bleibt bis R06 bestehen; Produktionskonfiguration, getrennte Datenbankrollen und Release-/Restore-Erprobung folgen in R12/R14. Inkompatibel manuell veränderte Session-Schemata werden nicht automatisch repariert.

### R05 – Öffentliche Rechte bewusst festlegen

- [x] Erledigt am 12. September 2026
- **Befund:** Gäste können Seiten auf oberster Ebene erstellen. Neue Seiten erhalten automatisch Gast-Leserechte, auch unter einer geschützten Elternseite. Bearbeitungsrechte erlauben derzeit ebenfalls die Verwaltung der Seitenberechtigungen.
- **Beschlossene Regel:** Neue Unterseiten erben dynamisch von ihrer Elternseite. Erstellung erfordert deren Bearbeitungsrecht, auch für Gäste. Hauptseiten angemeldeter Benutzer sind zunächst nur für Ersteller und Admins zugänglich. Wenn Gast-Erstellung für Hauptseiten erlaubt ist, entstehen öffentlich lesbare Seiten ohne automatische Gast-Bearbeitungsrechte. Abweichende Freigaben erfordern eine ausdrückliche Entscheidung; die Verwaltung ist vom Bearbeiten getrennt und Administratoren vorbehalten.
- **Abnahme:** Die beschlossene Berechtigungsmatrix ist dokumentiert und für Oberfläche und API getestet. Geschützte Inhalte werden nicht unbeabsichtigt veröffentlicht; bestehende Daten werden bei Regeländerungen berücksichtigt.
- **Umgesetzt:** Changeset `1.0.7` ergänzt den Vererbungsmodus; vorhandene Seiten behalten ihre bisherigen eigenen Rechte. Die gemeinsame Berechtigungsprüfung löst die Elternkette dynamisch auf und verweigert normalen Benutzern/Gästen Zugriff bei ungültiger oder zyklischer Vererbung. Admins können im Editor ausdrücklich zu eigenen Rechten wechseln (Kopie der aktuell wirksamen Freigaben) oder lokale Rechte zugunsten der Vererbung entfernen. Lokale Freigabeänderungen sind während der Vererbung gesperrt. Berechtigungsliste, Benutzer-/Gruppenauswahl, direkte Endpunkte und Berechtigungsfelder im Speicherformular verlangen Adminrechte; unerlaubte Felder werden vor Inhaltsänderungen abgelehnt.
- **Erstellung:** UI und REST verwenden denselben transaktionalen Erstellungsservice für Inhalt, Standardrechte und Labels. Auditdaten kommen aus der Sitzung; REST liefert die erzeugte Seiten-ID zurück. Neue Unterseiten erhalten keine zusätzlichen Ersteller-/Gastfreigaben, die spätere Elternänderungen umgehen könnten. `KR_ALLOW_GUEST_ROOT_CREATION` steuert Gast-Hauptseiten und ist standardmäßig `false`. Gast-Unterseiten richten sich unabhängig davon nach den Elternrechten. Regeln, Migration und Bedienung sind in [access-control.md](access-control.md) und README dokumentiert.
- **Geprüft:** `.\mvnw.cmd -B --no-transfer-progress verify`: **BUILD SUCCESS**, 121 Tests erfasst, davon **111 erfolgreich und 10 bereits zuvor deaktiviert**, keine Fehler. 31 neue Fälle prüfen bestehende Benutzer-/Gruppen-/Gast-/Admin-Rechte, fremde Berechtigungs-IDs, mehrstufige Vererbung und Entzug, explizite Ausnahmen und Rückkehr zur Vererbung, ungültige Hierarchien, Gast-Erstellung, private Hauptseiten, Auditdaten und Rollback sowie HTTP-/Formularzugriff und gerenderte Thymeleaf-Ansichten. Der MariaDB-Upgrade-Test bestätigt den Erhalt bestehender Seiten und Freigaben. `git diff --check` ohne Fehler.
- **Testgrenzen:** MariaDB-Tests verwenden echte SQL-Abfragen; HTTP-/Template-Tests verwenden die echte Filterkette, Controller und den Erstellungsservice mit gemockten DAOs. Ein interaktiver Browsertest der HTMX-Moduswechsel und vollständige HTTP-/Datenbank-/Storage-Integration bleiben in R13. Die Vererbung benötigt zusätzliche Abfragen entlang der Elternkette; Optimierung folgt in R10. Vorhandene lokale Ausnahmen bleiben bewusst unabhängig von Elternänderungen. Bestehende Gast-Bearbeitungsrechte bleiben erhalten und erlauben Gast-Unterseiten, aber keine Freigabeverwaltung.

### R06 – Passwortspeicherung und Login-Schutz modernisieren

- [x] Erledigt am 12. September 2026
- **Befund:** Eigene SHA-256-Passworthashes mit 1.000 Wiederholungen werden auch für neue Passwörter verwendet.
- **Umsetzung:** Etablierten PasswordEncoder mit geeignetem Verfahren und Kostenparametern einsetzen; vorhandene Hashes beim erfolgreichen Login migrieren; Passwortbehandlung in UI und API vereinheitlichen; Login-Drosselung und sichere Fehlerbehandlung ergänzen.
- **Abnahme:** Neue Passwörter verwenden das neue Verfahren. Bestandsnutzer können sich anmelden und ihre Hashes werden migriert. Ungültige Hashes verursachen keine unkontrollierten Serverfehler. Passwortwechsel und Drosselung sind getestet.
- **Umgesetzt:** Ein gemeinsamer `PasswordService` verwendet Springs PBKDF2-HMAC-SHA-256 mit 600.000 Iterationen, zufälligem 16-Byte-Salt und versioniertem Speicherformat. Admin-UI, REST-API und Erst-Admin setzen damit neue Passwörter. Neue Werte haben 16–128 Zeichen; Leerzeichen bleiben unverändert. Leere/fehlende Werte und der bisherige Maskierungswert `***` erhalten bei Updates das Passwort. Ungültige neue Werte ergeben 400; REST-Antworten enthalten weder Klartextpasswort noch Verifier.
- **Migration:** Erfolgreiche Legacy-Logins werden ohne rückwirkende Mindestlänge migriert. Das Schreiben vergleicht Benutzer-ID und bisherigen Hash und überschreibt dadurch keine gleichzeitige Passwortänderung. Benutzerkontext und abschließende Kontoprüfung verwenden die ursprüngliche Benutzer-ID. Defekte Hashformate und übergroße Legacy-Parameter werden begrenzt und kontrolliert abgelehnt. Die bekannten Demo-Verifier werden ausdrücklich nicht automatisch migriert, damit die Produktionssperre aus R04 bis zum tatsächlichen Passwortwechsel erhalten bleibt.
- **Drosselung und Sitzungen:** Changeset `1.0.8` speichert Zähler für normalisierte Loginnamen und Quelladressen in MariaDB. Standard sind fünf Versuche pro Konto und 30 pro Quelle in 60 Sekunden; alle Versuche werden vor dem Hashvergleich atomar reserviert. Mehrere Instanzen und Neustarts teilen dieselben Quoten. Die Anwendung übernimmt die Servlet-Gegenstellenadresse, ohne selbst Forwarded-Header auszuwerten. Passwortänderungen beenden bestehende Sitzungen beim nächsten Request über einen Verifier-Änderungsmarker. Vor R06 gespeicherte Sitzungen benötigen einmalig eine neue Anmeldung. Regeln, Konfiguration, Demo-Ausnahme und Rollbackgrenzen stehen in [authentication.md](authentication.md).
- **Geprüft:** `.\mvnw.cmd -B --no-transfer-progress verify`: **BUILD SUCCESS**, 154 Tests erfasst, davon **144 erfolgreich und 10 bereits zuvor deaktiviert**, keine Fehler. 33 zusätzliche Fälle prüfen moderne/Legacy-Hashes, Salt, Unicode/Leerzeichen und Eingabegrenzen, defekte Verifier, UI-/API-Konsistenz und fehlende Passwortausgabe, echte MariaDB-Migration samt konkurrierendem Passwortwechsel, Quoten über parallele Instanzen, Fensterablauf und Adressbegrenzung, Datenbankausfall, ignorierte fremde Forwarded-Header und Sitzungsinvalidierung. Der Installationstest bestätigt moderne Erst-Admin-Passwörter. `git diff --check` ohne Fehler.
- **Testgrenzen:** Hashkosten und Quoten müssen vor dem Release unter der tatsächlichen Produktionslast vermessen werden. Konten mit verbrauchten Quoten sind auch mit korrektem Passwort bis zum nächsten Fenster blockiert; Quellen hinter einem Proxy teilen ohne gezielte Konfiguration dessen Quote. Proxy-Vertrauen und Deployment bleiben in R12/R13. Vollständige HTTP-/JDBC-Sitzungsintegration und Betriebsüberwachung sind weiterhin offen. Eine Version vor R06 kann migrierte Passwörter nicht prüfen; der Rollbackweg muss dies berücksichtigen.

## 2. Abhängigkeiten und unterstützte Plattform

### R07 – POM bereinigen und Spring aktualisieren

- [ ] Offen
- **Befund:** Spring Boot 3.5.14; zusätzlich Spring Session 1.3.5.RELEASE neben Core/JDBC 3.5.6. Ungenutztes JavaFaker zieht SnakeYAML mit Android-Classifier 1.23 neben SnakeYAML 2.4 in den Klassenpfad.
- **Umsetzung:** Alte Session-Abhängigkeit und ungenutztes JavaFaker entfernen; redundante Logging- und WebJars-Locator-Abhängigkeiten bereinigen; Spring-Boot-4.x-Migration auf eine unterstützte Linie durchführen. Explizite Liquibase-Version gegen das Boot-Management prüfen. Transitive Abhängigkeiten wie die derzeit verwendete `javax.xml.bind`-API beim Bereinigen berücksichtigen.
- **Abnahme:** Keine konkurrierenden alten Session-/SnakeYAML-Generationen im Klassenpfad. Unterstützte Boot-Version, erfolgreiche Tests und echter Anwendungsstart. Dependency-Baum und Schwachstellenscan sind geprüft; relevante Funde sind behoben oder mit konkreter Begründung bewertet.

### R08 – Sanitizer, Editor und weitere Bibliotheken aktualisieren

- [ ] Offen
- **Umsetzung:** HTML Sanitizer von 20240325.1 mindestens auf 20260102.1 oder eine geeignete neuere Version aktualisieren; CKEditor vom nicht mehr unterstützten Classic-Predefined-Build auf eine unterstützte Installation migrieren; MinIO-SDK, Commons IO und verwendete Frontend-Bibliotheken gezielt prüfen und aktualisieren.
- **Einordnung:** Für die alte Sanitizer-Version existiert CVE-2025-66021. Die speziellen Voraussetzungen der veröffentlichten Schwachstelle wurden in der aktuellen Policy nicht festgestellt; ein ausnutzbares XSS wurde im Review nicht nachgewiesen.
- **Abnahme:** Sanitizer-Regressionstests bestehen. Editor, Speichern, HTMX-Navigation und statische Ressourcen funktionieren im Browser. Bestehende Inhalte bleiben nutzbar. Upload und Download funktionieren mit dem aktualisierten SDK.

## 3. Datenintegrität und Anwendungscode

### R09 – Schreibvorgänge atomar machen

- [ ] Offen
- **Befund:** Beim Bearbeiten werden Seiteninhalt, Labels und Berechtigungen noch in getrennten Transaktionen gespeichert. Ein später Fehler kann teilweise gespeicherte Änderungen hinterlassen. Die Neuerstellung über UI und REST ist seit R05 bereits gemeinsam transaktional abgesichert.
- **Umsetzung:** Zusammengehörige Änderungen in transaktionale Anwendungsservices verschieben; Auditfelder mit dem tatsächlichen Benutzer befüllen. Dateiablage und Metadaten benötigen eine definierte Fehlerbehandlung, da ein Object Store nicht an der Datenbanktransaktion teilnimmt.
- **Abnahme:** Ein Fehler beim Speichern von Labels oder Rechten rollt den gesamten fachlichen Datenbankvorgang zurück. Uploadfehler erzeugen keine als erfolgreich angezeigten, unvollständigen Dateien. Auditfelder sind korrekt.

### R10 – Validierung, Fehlerfälle und Suchabfragen verbessern

- [ ] Offen
- **Befund:** Der Inhaltsfilter verwendet `:description`, bindet aber `content`. Validierung und Fehlerantworten sind uneinheitlich; Suche lädt unbeschränkt Ergebnisse und prüft Rechte anschließend einzeln.
- **Umsetzung:** SQL-Parameterfehler beheben; Eingaben und Seitengrößen begrenzen; konsistente 400/403/404-Antworten vorsehen; keine internen Exception-Texte ausgeben; Suche paginieren und unnötige Einzelabfragen reduzieren. Berechtigungen müssen vor der fachlichen Pagination berücksichtigt werden.
- **Abnahme:** Inhaltsfilter funktioniert. Ungültige Eingaben und fehlende Datensätze ergeben kontrollierte Antworten. Suchergebnisse bleiben berechtigungskonform und sind bei repräsentativer Datenmenge begrenzt und ausreichend schnell.

### R11 – Speichertreiber und Uploads absichern

- [ ] Offen
- **Befund:** Auch bei `storage.driver=file` wird MinIO initialisiert und kontaktiert. Uploads verwenden MD5 zur Inhaltsadressierung, lesen die Datei zum Hashen vollständig in den Speicher und setzen Audit-Benutzer fest auf `1`.
- **Umsetzung:** Storage-Beans bedingt aktivieren; lokale Speicherung ohne MinIO ermöglichen; Streaming und geeignetes Hashverfahren mit Bestandskompatibilität vorsehen; Dateinamen, Downloadheader und Uploadgrenzen sauber behandeln. Gleichzeitige Uploads und fehlgeschlagene Schreibvorgänge berücksichtigen.
- **Abnahme:** Dateispeicher startet ohne MinIO-Konfiguration und Netzwerkzugriff auf MinIO. Beide Treiber bestehen Upload-/Downloadtests einschließlich Grenz- und Fehlerfällen. Bereits gespeicherte Dateien bleiben lesbar.

## 4. Betrieb und Release-Freigabe

### R12 – Produktionskonfiguration und Lieferprozess herstellen

- [ ] Offen
- **Umsetzung:** Produktionsprofil mit verpflichtenden Zugangsdaten, eingeschränktem Datenbankbenutzer und aktivem Template-Cache erstellen; HTTPS-/Proxy- und Cookie-Konfiguration dokumentieren und testen. Container als unprivilegierten Benutzer betreiben, Images versionieren und Datenbank-/Storage-Ports nur gezielt freigeben. CI-Prüfung von Veröffentlichung trennen: PRs veröffentlichen keine Images; Releases stammen aus freigegebenen Branches beziehungsweise Tags.
- **Storage-Entscheidung:** Für den archivierten, nicht mehr gepflegten MinIO-Community-Server einen tragfähigen Betriebsweg bestimmen: lokaler Dateispeicher oder ein gepflegter beziehungsweise unterstützter S3-Dienst. Die Auswahl ist offen.
- **Abnahme:** Dokumentierter Start in einer frischen Umgebung; fehlende Pflichtkonfiguration führt zu verständlichen Fehlern. CI prüft PRs ohne Registry-Zugangsdaten und veröffentlicht nur freigegebene Builds. Storage-Entscheidung und Betriebsvoraussetzungen sind dokumentiert.

### R13 – Integrationstests und Betriebsüberwachung ergänzen

- [ ] Offen
- **Umsetzung:** Die beiden deaktivierten Integrationstestklassen ersetzen oder reparieren und aktivieren; isolierte MariaDB-/Storage-Tests, beispielsweise mit Testcontainers, etablieren. Echten Login, Rollen und Objektberechtigungen, Kommentare, Sterne, Labels, Dateien sowie Neuinstallation und Upgrade testen. Health-/Readiness-Prüfungen, brauchbare Logs und erforderliche Metriken ergänzen; Management-Endpunkte absichern.
- **Abnahme:** Tests benötigen keine private lokale Konfiguration oder produktiven Dienste. Frische Installation und Upgrade laufen automatisiert. Ausfall von Datenbank oder gewähltem Storage wird erkennbar; sensible Managementinformationen sind nicht öffentlich.

### R14 – Wiederherstellung und Release erproben

- [ ] Offen
- **Umsetzung:** Backup von Datenbank und Dateien, Wiederherstellung, Upgrade und Rückkehr zur vorherigen Version dokumentieren. Datenkonsistenz zwischen Datenbank und Storage berücksichtigen. Release-Version, Konfigurationsbeispiele und Betriebsanleitung vervollständigen; benötigte Beispieldateien versionieren.
- **Abnahme:** Restore in eine leere Umgebung wurde praktisch durchgeführt; Inhalte, Rechte und Anhänge sind nutzbar. Upgrade und dokumentierter Rollbackweg wurden mit repräsentativen Bestandsdaten getestet. Release-Artefakt ist reproduzierbar zuordenbar, Tests und Sicherheitsprüfung sind bestanden und alle verbleibenden Einschränkungen dokumentiert.

## 5. Mehrbenutzerbetrieb und weitere Verbesserungen

### R15 – Bearbeitungskonflikte und Versionshistorie

- [ ] Offen
- **Umsetzung:** Gleichzeitiges Bearbeiten mittels Versionsprüfung erkennen; vorhandene `page_history`-Struktur zu einer nutzbaren Historie mit Wiederherstellung ausbauen. Löschverhalten einschließlich Unterseiten und zugehöriger Dateien ausdrücklich definieren.
- **Abnahme:** Veraltete Bearbeitungsstände überschreiben neue Inhalte nicht stillschweigend. Frühere Versionen lassen sich berechtigungskonform anzeigen und wiederherstellen. Löschen hinterlässt keine unerwartet beschädigte Hierarchie.
- **Einordnung:** Konflikterkennung ist vor breitem Mehrbenutzerbetrieb erforderlich. Umfang und Zeitpunkt der Historienoberfläche können gesondert priorisiert werden.

## Nachweise der Bestandsaufnahme

- `mvn -B test` bei der ursprünglichen Bestandsaufnahme: Anwendung kompiliert unter Java 25; Testkompilierung scheitert am Konstruktoraufruf in `PageControllerTest`. Dieser Befund ist durch R01 behoben; das aktuelle Prüfergebnis steht dort.
- Maven-Dependency-Baum: alte Spring-Session- und zusätzliche SnakeYAML-Generation bestätigt.
- Isolierter Token-Test: sowohl `token.isAuthenticated()` als auch Springs Authentifizierungsprüfung liefern `false`.
- Beide vollständigen Integrationstestklassen sind mit `@Disabled` markiert. Ein vollständiger Schwachstellenscan, Deploymenttest und Restoretest wurden im Review nicht durchgeführt.

Referenzen, geprüft bei der Bestandsaufnahme:

- [Spring Boot 3.5.16: Ende des OSS-Supports](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
- [OWASP: Passwortspeicherung](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [HTML Sanitizer: CVE-2025-66021 und Voraussetzungen](https://github.com/OWASP/java-html-sanitizer/security/advisories/GHSA-g9gq-3pfx-2gw2)
- [HTML Sanitizer: Release mit Sicherheitsfix](https://github.com/OWASP/java-html-sanitizer/releases/tag/release-20260101.1)
- [HTML Sanitizer: Folgerelease 20260102.1](https://github.com/OWASP/java-html-sanitizer/releases/tag/release-20260102.1)
- [CKEditor: Migration der Installationsform](https://ckeditor.com/docs/ckeditor5/latest/updating/nim-migration/migration-to-new-installation-methods.html)
- [MinIO: Wartungsstatus des Serverprojekts](https://github.com/minio/minio)

Referenzen für R01:

- [Apache Maven: Distributionen](https://maven.apache.org/download.cgi)
- [Apache Maven Wrapper: Aktualisierung und Prüfsummen](https://maven.apache.org/tools/wrapper/)

Referenzen für R03:

- [Spring Security: SecurityContext und Sitzungspersistenz](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html)
- [Spring Security: Logout und Sitzungsinvalidierung](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
