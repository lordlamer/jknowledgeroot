# Passwörter, Login-Schutz und Sitzungen

## Passwortformat und Migration

Neue Passwörter aus Admin-Oberfläche, REST-API und Erst-Admin-Einrichtung verwenden
Springs `Pbkdf2PasswordEncoder` mit HMAC-SHA-256, 600.000 Iterationen, einem zufälligen
16-Byte-Salt und 256-Bit-Hash. Die gespeicherte Kennung `{pbkdf2-sha256-v1}` legt
Verfahren und Parameter eindeutig fest. PBKDF2 verwendet die vorhandene
JDK-/Spring-Kryptografie; eine zusätzliche Kryptobibliothek ist dafür nicht nötig.
Die Parameter entsprechen der von OWASP genannten PBKDF2-HMAC-SHA-256-Einstellung.
[OWASP Passwortspeicherung](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
[Spring PasswordEncoder](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).

Neue Passwörter müssen 16–128 Zeichen lang sein und dürfen nicht ausschließlich
aus Leerraum bestehen. Führende und nachfolgende Leerzeichen bleiben erhalten;
UI und API schneiden sie nicht ab. Beim Aktualisieren bedeuten ein fehlender Wert,
`null`, ein leerer String oder der bisher unterstützte Maskierungswert `***`, dass
das Passwort unverändert bleibt. Andere zu kurze oder reine Leerraumwerte ergeben
400. Passwortwerte und Verifier werden in REST-Antworten nicht ausgegeben.

Bestehende Legacy-Hashes werden beim erfolgreichen Login geprüft und anschließend
durch das neue Format ersetzt. Dabei gilt die neue Mindestlänge nicht rückwirkend;
ein bisher gültiges kurzes Passwort bleibt zunächst nutzbar. Login-Eingaben sind
auf 1.024 Zeichen begrenzt. Fehlerhafte, unbekannte oder unvertretbar große
Legacy-Parameter werden kontrolliert abgelehnt. Die übernommenen Legacy-Formate
haben 16-stellige hexadezimale Salts und 1–999.999 Wiederholungen; die Anwendung
hat bislang standardmäßig 1.000 Wiederholungen erzeugt. Sonderimporte außerhalb
dieser Formate müssen vor dem Upgrade geprüft werden.

Die Migration vergleicht Benutzer-ID und bisherigen Hash beim Schreiben. Ein
gleichzeitig gesetztes neues Passwort wird nicht überschrieben. Bei einem solchen
Konflikt schlägt der Login kontrolliert fehl und muss mit den aktuellen Daten
wiederholt werden. Auch mehrere gleichzeitige erste Logins können einen solchen
einmaligen Konflikt auslösen. Die Kontodaten werden anhand der beim Passwortabruf
ermittelten ID geladen, damit Umbenennung und Wiedervergabe eines Logins keinen
Kontowechsel verursachen.

**Demo-Ausnahme:** Der bekannte mitgelieferte Demo-Hash wird beim Login nicht
automatisch ersetzt. Andernfalls wäre er für die Produktionssperre aus R04 nicht
mehr erkennbar, obwohl weiterhin das bekannte Demo-Passwort gilt. Diese Konten
benötigen ausdrücklich neue Passwörter oder müssen deaktiviert werden, bevor
die Installation außerhalb des Demo-Modus startet. Siehe [Installation](installation.md).

## Login-Drosselung

Changeset `1.0.8-login-attempts` ergänzt persistente, gemeinsam genutzte Zähler in
MariaDB. Die Anwendung reserviert vor jedem Passwortvergleich einen Versuch pro
normalisiertem Loginnamen und pro Quelladresse. Die Zähler gelten damit auch nach
einem Neustart und für mehrere Instanzen mit derselben Datenbank.

| Einstellung | Standard | Bedeutung |
| --- | --- | --- |
| `KR_LOGIN_ACCOUNT_ATTEMPTS` | `5` | Versuche je normalisiertem Loginnamen und Zeitfenster |
| `KR_LOGIN_SOURCE_ATTEMPTS` | `30` | Versuche je Quelladresse und Zeitfenster, über alle Loginnamen |
| `KR_LOGIN_WINDOW_SECONDS` | `60` | Länge eines festen Zeitfensters |

Alternativ heißen die Properties `knowledgeroot.login.account-attempts`,
`knowledgeroot.login.source-attempts` und `knowledgeroot.login.window-seconds`.
Quoten müssen zwischen 1 und 1.000.000 liegen, Zeitfenster zwischen 1 und 3.600 Sekunden.
Ungültige Konfiguration verhindert den Start. Ein Fenster beginnt mit dem ersten
Versuch; abgelaufene Fenster beginnen bei der nächsten Anfrage neu.

Gezählt werden alle Versuche, auch erfolgreiche. Die Reservierung vor dem
Passwortvergleich verhindert, dass parallele Anfragen das Limit überlaufen.
Erfolg setzt Zähler nicht zurück. Nach ausgeschöpfter Quote werden auch korrekte
Zugangsdaten bis zum nächsten Fenster abgelehnt. Dadurch können wiederholte
Anfragen an einen Loginnamen dessen Anmeldung vorübergehend behindern; die
Grenzwerte müssen zur tatsächlichen Nutzung passen. Geteilte Quelladressen,
beispielsweise hinter einem Unternehmensproxy, teilen sich die Adressquote.

Verwendet wird die vom Servlet-Container gelieferte Gegenstellenadresse.
Die Anwendung wertet `X-Forwarded-For` für diese Prüfung nicht selbst aus.
Bei Betrieb hinter einem Reverse Proxy muss die Weiterleitungs-/Vertrauenskonfiguration
gezielt eingerichtet werden; dies bleibt Bestandteil von R12. Ohne diese
Konfiguration zählt die Proxy-Adresse als gemeinsame Quelle.

Die Datenbank speichert Loginnamen und Adressen nur als Hashes mit getrennten
Namensräumen. Dies ist Pseudonymisierung, keine Anonymisierung. Fenster, die über
eine Stunde alt sind, werden bei weiteren Login-Versuchen in begrenzten Batches
bereinigt. Ohne weitere Anfragen bleiben diese Einträge bis zur nächsten Bereinigung
bestehen. Datenbankfehler bei Limitprüfung oder Kontoprüfung lassen den Login scheitern.
Die Bereinigung löscht einzeln ausgewählte, weiterhin abgelaufene Einträge;
bei konkurrierenden Datenbankzugriffen wird sie auf eine spätere Anfrage verschoben.

Fehlende Konten, falsche Passwörter und Drosselung verwenden dieselbe allgemeine
Fehlerseite. Nicht vorhandene Konten und defekte/Legacy-Hashes führen innerhalb
der Quote ebenfalls einen modernen Passwortvergleich aus, um große Laufzeitunterschiede
zu verringern. Es wird keine konstante Antwortzeit über Datenbank und Netzwerk zugesichert.

## Auswirkungen auf Sitzungen und Upgrades

Der Sitzungskontext enthält einen SHA-256-Änderungsmarker des gespeicherten
Passwort-Verifiers, weder das Passwort noch den Verifier selbst. Ändert sich der
Marker, beendet die bestehende R03-Sitzungsprüfung die Sitzung beim nächsten
Request. Das gilt auch nach einer administrativen Passwortänderung und für
mehrere Sitzungen eines Kontos. Bereits laufende Requests werden nicht abgebrochen.

Vor R06 gespeicherte Sitzungen haben keinen Marker und erfordern nach dem Upgrade
eine neue Anmeldung. Die bisherige Serialisierungskennung des Benutzerkontexts
bleibt erhalten, damit diese Sitzungen kontrolliert abgemeldet werden können.
Auch eine Hashmigration kann ältere Sitzungen desselben Kontos ungültig machen.

Ein Rollback auf eine Version vor R06 kann bereits migrierte Passwörter nicht
prüfen. Den Rückweg deshalb mit kompatibler Anwendungsversion beziehungsweise
dem vorbereiteten Datenbankbackup planen. Die Anwendung ist weiterhin erst nach
den übrigen Roadmap-Punkten zur Produktion freizugeben.

## Prüfungen und verbleibende Arbeit

Tests prüfen moderne und alte Hashes, Salt, Leerzeichen/Unicode, Eingabegrenzen,
ungültige Hashformate, identische Regeln in UI/API, ausbleibende Passwortausgabe,
Migration gegen MariaDB und einen konkurrierenden Passwortwechsel. Parallele
Limiter-Instanzen dürfen die Accountquote nicht überschreiten; Fensterablauf,
Adressquote und Verhalten bei Neustart sind abgedeckt. HTTP-Tests prüfen
gesperrte Logins, Quelladressen und Sitzungsinvalidierung nach Passwortänderungen.

Hashkosten und Quoten sind noch unter der tatsächlichen Produktionslast zu messen.
Reverse-Proxy-Betrieb, ein vollständiger Deploymenttest, Monitoring und gegebenenfalls
zusätzliche Begrenzung am vorgeschalteten Proxy folgen in R12/R13. R07 ergänzt einen
HTTP-Test des gebauten JARs samt JDBC-Sitzung über einen Neustart; siehe
[Abhängigkeiten und Prüfungen](dependencies.md). Die beiden zuvor
deaktivierten allgemeinen Integrationstestklassen bleiben bis R13 offen.
