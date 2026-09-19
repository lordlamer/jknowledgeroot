# Protokoll für die Betriebsabnahme

Dieses Protokoll ist für die konkrete Staging-/Produktionsumgebung auszufüllen.
Lokale Tests ersetzen die Abnahme auf dem Zielsystem nicht. Der aktuelle Kandidat
ist `1.0.0-rc.2`; eine Produktionsfreigabe wurde noch nicht erteilt.

## Umgebung und Verantwortliche

| Angabe | Wert |
| --- | --- |
| Datum / prüfende Person | Offen |
| Domain und Staging-/Produktionssystem | Offen |
| Betreiber / Vertretung / Alarmempfänger | Offen |
| Release-Commit, Registry-Digest und Manifest | Offen; konkrete Artefakte festhalten |
| Datenbankversion, Speicherort und Proxyversion | Offen |
| CPU/RAM, freier DB-/Datei-/Temp-/Backupplatz | Offen |
| Nutzung / erwartete gleichzeitige Nutzer und Datenmenge | Intern und öffentlich; anfangs eher klein, später auch größere Bestände. Konkrete Parallelität und Datenmenge offen. |
| Zulässige Antwortzeiten und Fehlerquote | Vor Lastprüfung festlegen |
| RPO, RTO und Aufbewahrungsdauer | Vor Restoreprüfung festlegen |

Keine Passwörter, Tokens oder privaten Schlüssel in dieses Dokument eintragen.
Eine ausgefüllte Kopie mit tatsächlichen Systemdaten gehört in das Betriebsarchiv.

## Prüfungen auf dem Zielsystem

| Prüfung | Vorgehen und erwarteter Nachweis | Status |
| --- | --- | --- |
| Artefakte | Manifest und Registry-Digest stimmen mit dem freigegebenen Commit überein; Maven-/npm- und OS-Scanberichte vorhanden. | Offen |
| Restbefunde | Laufzeitnachweise aus R19 und beide endgültigen Imageberichte prüfen; `container-findings.md` bewertet die App, `database-findings.md` die Datenbank. Verbleibende Befunde auf tatsächliche Arbeitsabläufe und Host-/Volume-Rechte beziehen; Verantwortliche, Entscheidung und nächsten Prüftermin festhalten. | Offen |
| Installation/Upgrade | In Staging dieselbe Konfiguration und einen geschützten repräsentativen Datenbestand verwenden; Migration und Start ohne Fehler. Alte Writer bleiben gestoppt. | Offen |
| TLS und Proxy | Gültiges Zertifikat, HTTPS-Zugriff, korrekte Weiterleitungen, Secure/HttpOnly/SameSite-Cookies; App-Port nur für den vorgesehenen Proxy zugänglich. | Offen |
| Rechte und Gäste | Reale Rollen/Gruppen sowie Gastzugriff prüfen: öffentliche Seite erreichbar, private Seite und deren Datei unzugänglich; Unterseiten erben die vereinbarte Freigabe. | Offen |
| Redaktion und Historie | Eigene Altinhalte bearbeiten; zwei Tabs erzeugen einen Konflikt ohne Verlust des Entwurfs. Historie ansehen und wiederherstellen. Zwei gespeicherte Stände, auch über mehrere Ergebnisseiten hinweg, und einen Stand mit der aktuellen Seite vergleichen; Text-, Link- und Formatierungsänderungen prüfen. | Offen |
| Seiten verschieben | Als Administrator eine Seite samt Unterseiten verschieben: IDs, Links und Anhänge bleiben erhalten; vererbte Rechte folgen dem neuen Elternteil, lokale Freigaben bleiben bestehen. Beim Verschieben auf die oberste Ebene bleiben die bisherigen wirksamen Freigaben erhalten. Zugriff mit Gast und realen Gruppen vor/nach dem Verschieben prüfen; ein vorher geöffnetes Bearbeitungsformular muss einen Konflikt melden. | Offen |
| Passwort im Profil | Falsches aktuelles Passwort und abweichende Wiederholung ändern das Passwort nicht. Erfolgreicher Wechsel verlangt eine neue Anmeldung; auch eine zweite zuvor geöffnete Sitzung verliert beim nächsten Request ihre Anmeldung. Das alte Passwort funktioniert nicht mehr. Vorhandenes Zurücksetzen durch Administratoren prüfen; E-Mail-Reset gehört vorerst nicht zum Umfang. | Offen |
| Mobile Bedienung | Profil bei schmalem Bildschirm bedienen; Passwortfelder und Aktionen bleiben sichtbar. Seitenleiste öffnen/schließen, Seite verschieben und Vergleichsansicht mit eigenen Inhalten prüfen. | Offen |
| Große Seitenbäume | Ebenen mit mehr als 50 sichtbaren Seiten vollständig nachladen. Aufgeklappte Nachbarzweige bleiben erhalten; aktive Seite, Sterne und Wiederherstellung nach Neuladen funktionieren. Gast-/Gruppenrechte auch beim Nachladen und nach Rechteentzug prüfen. | Offen |
| Löschen und Anhänge | Eltern mit Unterseiten lassen sich nicht löschen. Gelöschte Seiten/Downloads sind verborgen; Administrator stellt Eltern und Kinder samt Anhangzugriff wieder her. | Offen |
| Last und Loginquoten | Repräsentative Suche, Navigation, Login und Uploads mit vereinbarter Parallelität ausführen; p95, Fehlerquote, CPU/RAM, DB-Pool und freien Platz aufzeichnen. | Offen |
| Alarmierung | In Staging DB-/Speicherfehler kontrolliert auslösen; Readiness wird DOWN und externe Alarmierung erreicht tatsächlich Betreiber und Vertretung. Wiederanlauf prüfen. | Offen |
| Sicherung | Anwendung und andere Writer stoppen; gemeinsame DB-/Dateisicherung mit Prüfsummen erstellen und zugriffsgeschützt außerhalb des App-Hosts ablegen. | Offen |
| Restore und RTO | Auf einem leeren separaten Ziel wiederherstellen, Konten/Rechte/Inhalte/Dateibytes prüfen; Datenstand und gemessene Dauer erfüllen RPO/RTO. | Offen |
| Rollback | Vor-Upgrade-Snapshot mit altem Image separat wiederherstellen; Verlust späterer Änderungen ist im Ablauf ausdrücklich berücksichtigt. | Offen |
| Lieferprozess | Gehostete CI erfolgreich; Branch-/Tag-Schutz sowie GitHub-Umgebung `release` mit Reviewern und ausschließlich dort verfügbaren Registry-Secrets eingerichtet. | CI einschließlich Release-Schutztests und Graph-Übermittlung für `087eae0` am 19. September erfolgreich ([Nachweis](https://github.com/lordlamer/jknowledgeroot/actions/runs/35439935570)). Basis-Rulesets und PR-Pflicht mit `verify` aktiv. Eigene Release-Bestätigung durch `lordlamer`, Administrator-Bypass aus, nur Tags `v*`; API-Abgleich und lesender Release-Check erfolgreich. Secret-Umstellung sowie tatsächlicher PR-Merge und Release-Ablauf offen. |

Die jeweiligen Befehle und Grenzen stehen in [production.md](production.md),
[monitoring.md](monitoring.md), [recovery.md](recovery.md),
[page-history.md](page-history.md), [product-functions.md](product-functions.md)
und [image-security.md](image-security.md).
Die anfänglich fehlenden Basisregeln wurden in R32 aktiviert: Ruleset 23697742
schützt `master` gegen Force-Pushes und Löschen, Ruleset 23697743 schützt
bestehende `v*`-Tags gegen Änderung und Löschen. R33 ergänzt Ruleset 23698955
für PR-Pflicht und erfolgreiches `verify` ohne fremde Review-Freigabe sowie die
Umgebung `release` mit eigener Bestätigung durch `lordlamer`.
Die beiden Registry-Secrets liegen noch auf Repository-Ebene; vor Veröffentlichung
in `release` hinterlegen und anschließend die Repository-Kopien entfernen.
Der lesende Release-Check besteht mit der neuen Umgebung; der tatsächliche
Publish-Ablauf bleibt abzunehmen. Nachweise der aktiven Konfiguration:
`target/r32-active-rulesets.json` und `target/r33-repository-after.json`;
Details in
[repository-protection.md](repository-protection.md).
Der [lokale Proxytest](proxy-testing.md) liefert einen zusätzlichen technischen
Nachweis für das nginx-Beispiel; die TLS-/Proxy-Abnahme des Zielsystems bleibt offen.
Fehler-/Lasttests nur in einer dafür vorgesehenen Umgebung und einem abgestimmten
Zeitfenster ausführen; keine bestehenden produktiven Volumes als Testziel verwenden.
Für einen ersten wiederholbaren Lesevergleich steht der
[synthetische Lasttest](load-testing.md) bereit. Er ersetzt die Prüfung mit
eigenen Daten, Schreib-/Uploadlast und vereinbarten Grenzwerten nicht.

## Entscheidung

| Angabe | Ergebnis |
| --- | --- |
| Offene Befunde mit Verantwortlichem und Termin | Offen |
| Abweichungen und deren begründete Bewertung | Offen |
| Freigebender Betreiber / Zeitpunkt | Nicht freigegeben |
| Freigegebener Commit / Image-Digest | Offen |
| Nächster Restore-Test und nächste Sicherheitsprüfung | Offen |

Die Veröffentlichung wird erst nach dokumentierter Entscheidung gemäß
[release.md](release.md) ausgeführt. Ein ausgefülltes Protokoll erstellt weder
automatisch einen Tag noch veröffentlicht oder startet es ein Image.
