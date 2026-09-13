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
| Erwartete gleichzeitige Nutzer und Datenmenge | Offen |
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
| Redaktion | Eigene Altinhalte bearbeiten; zwei Tabs erzeugen einen Konflikt ohne Verlust des Entwurfs. Historie ansehen und wiederherstellen. | Offen |
| Löschen und Anhänge | Eltern mit Unterseiten lassen sich nicht löschen. Gelöschte Seiten/Downloads sind verborgen; Administrator stellt Eltern und Kinder samt Anhangzugriff wieder her. | Offen |
| Last und Loginquoten | Repräsentative Suche, Navigation, Login und Uploads mit vereinbarter Parallelität ausführen; p95, Fehlerquote, CPU/RAM, DB-Pool und freien Platz aufzeichnen. | Offen |
| Alarmierung | In Staging DB-/Speicherfehler kontrolliert auslösen; Readiness wird DOWN und externe Alarmierung erreicht tatsächlich Betreiber und Vertretung. Wiederanlauf prüfen. | Offen |
| Sicherung | Anwendung und andere Writer stoppen; gemeinsame DB-/Dateisicherung mit Prüfsummen erstellen und zugriffsgeschützt außerhalb des App-Hosts ablegen. | Offen |
| Restore und RTO | Auf einem leeren separaten Ziel wiederherstellen, Konten/Rechte/Inhalte/Dateibytes prüfen; Datenstand und gemessene Dauer erfüllen RPO/RTO. | Offen |
| Rollback | Vor-Upgrade-Snapshot mit altem Image separat wiederherstellen; Verlust späterer Änderungen ist im Ablauf ausdrücklich berücksichtigt. | Offen |
| Lieferprozess | Gehostete CI erfolgreich; Branch-/Tag-Schutz sowie GitHub-Umgebung `release` mit Reviewern und ausschließlich dort verfügbaren Registry-Secrets eingerichtet. | Offen |

Die jeweiligen Befehle und Grenzen stehen in [production.md](production.md),
[monitoring.md](monitoring.md), [recovery.md](recovery.md),
[page-history.md](page-history.md) und [image-security.md](image-security.md).
Fehler-/Lasttests nur in einer dafür vorgesehenen Umgebung und einem abgestimmten
Zeitfenster ausführen; keine bestehenden produktiven Volumes als Testziel verwenden.

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
