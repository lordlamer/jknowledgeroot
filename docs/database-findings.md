# Bewertung der Datenbank-Containerbefunde

Stand: 13. September 2026, R20; ergänzt am 19. Oktober 2026 (R38) auf Basis des Scans mit 28 MEDIUM und sieben LOW,
siehe [image-security.md](image-security.md). Diese technische Bewertung betrifft das gemeinsam
mit Knowledgeroot gebaute MariaDB-12.3.3-Image für eine einzelne Instanz.
Sie ersetzt weder die Bewertung des Hostsystems noch die betriebliche Freigabe.
Die [App-Bewertung](container-findings.md) gilt nicht automatisch für MariaDB.
Scannerberichte und Schweregradregel bleiben unverändert; es gibt keine Ausnahmen.

## Entfernte Pakete und erhaltene Funktionen

Der R19-Bericht enthält 21 Paketbefunde zu zwölf CVEs. Davon betrifft ein
mittlerer Befund `libsqlite3-0`: [CVE-2026-39113](https://ubuntu.com/security/CVE-2026-39113)
beschreibt einen Pufferfehler in der SQLite-SQLar-Erweiterung. Ubuntu führt Noble
als betroffen. Die Entfernung behauptet keine Ausnutzbarkeit über MariaDB und
keinen neuen Herstellerfix.

Im geprüften Image benötigt ausschließlich `gpg` die SQLite-Bibliothek.
Der simulierte Paketplan entfernt mit `apt-get purge gpg libsqlite3-0` genau
zwei Pakete. Der Dockerbuild übernimmt diesen Plan ohne `autoremove`.
MariaDB-Entrypoint, Healthcheck und die ausgelieferten Start-/Recovery-Skripte
rufen GPG nicht auf. Die Anwendung nutzt MariaDB und keinen SQLite-Speicher.
`gpgv` und die vorhandenen APT-Schlüssel bleiben für die Paketprüfung erhalten.
Eigene Initialisierungsskripte, die GPG oder SQLite benötigen, sind damit nicht
abgenommen. Verschlüsselung oder Signaturen eigener Backups auf dem dafür
vorgesehenen Host beziehungsweise Backupdienst durchführen.

Perl wird unter anderem von `mariadb-server` benötigt und bleibt erhalten.
Die Kette `libelf1t64` → `libbpf1` → `iproute2` wird ebenfalls beibehalten:
Eine Entfernung würde zusätzlich `mariadb-server-galera` entfernen. Die
Paketabhängigkeiten werden nicht gewaltsam aufgelöst. Galera ist im
Referenz-Compose nicht konfiguriert; Clusterbetrieb bleibt gesondert zu prüfen.

## Verbleibende Befunde

Der abschließende Scan erfasst **149 Pakete und 20 Paketbefunde zu elf CVEs**:
13 MEDIUM und sieben LOW, keine HIGH/CRITICAL/UNKNOWN. SQLite ist nicht mehr
im Paketbestand. Maßgeblich ist die konkrete Image-ID im R20-Roadmapnachweis.
Die Angaben LOW/MEDIUM folgen dem Ubuntu-basierten Scannerbericht; allgemeine
CVSS-Werte können davon abweichen. Insbesondere ist das keine Herabstufung
durch das Projekt.

| CVE / Pakete | Voraussetzung und konkret geprüfter Stand | Verbleibende Aufgabe |
| --- | --- | --- |
| [CVE-2026-18374](https://ubuntu.com/security/CVE-2026-18374), `libc6`, `libc-bin` (2 MEDIUM) | Fehler bei angreiferkontrolliertem `fopen`-Modus mit problematischem `,ccs=`. MariaDB bindet glibc dynamisch ein. Unsere Java-/Betriebsskripte stellen keine Schnittstelle für solche nativen Modusstrings bereit. | Kein vollständiger Nachweis für sämtliche MariaDB-, Plugin- und Bibliothekspfade. Herstellerfix beobachten; zusätzliche Plugins und eigene native Funktionen neu bewerten. |
| [CVE-2026-15534](https://ubuntu.com/security/CVE-2026-15534), [CVE-2026-19487](https://ubuntu.com/security/CVE-2026-19487), `perl`, `perl-base`, `perl-modules-5.38`, `libperl5.38t64` (8 MEDIUM) | Fehler in Perls Regex-Verarbeitung. Perl ist eine echte Paketabhängigkeit; unser Entrypoint-/Healthcheck-/Recoveryablauf enthält keine direkte Perl-Verarbeitung von Nutzereingaben. `mariadbd` bindet für Regex-Verarbeitung `libpcre2-8` ein. | Daraus folgt kein Beweis für alle mitgelieferten Verwaltungsprogramme. Fremde Logs/Eingaben nicht ungeprüft mit solchen Werkzeugen verarbeiten; eigene Wartungsskripte und Plugins prüfen. Der aktuelle Noble-Stand ist von Fixes anderer Ubuntu-Reihen zu unterscheiden. **Behoben:** Der MariaDB-Digest vom 17. Oktober 2026 enthält Perl `5.38.2-3.2ubuntu0.6` und `libdbi-perl 1.643-4ubuntu0.4`; seit R38 nicht mehr im Scan. |
| [CVE-2026-18477](https://ubuntu.com/security/CVE-2026-18477), [CVE-2026-18508](https://ubuntu.com/security/CVE-2026-18508), `tar` (2 MEDIUM) | Betreffen inkrementelle Dumpdir-Wiederherstellung beziehungsweise `--one-top-level`. Unser Datenbankbackup/-restore nutzt SQL; Datei-Vollarchive werden im App-Container verarbeitet. Der MariaDB-Entrypoint unterstützt zusätzlich andere Archiv-Initialisierungen, die unser Compose nicht verwendet. | Nur eigene geschützte Sicherungen und die dokumentierte SQL-Wiederherstellung zulassen. Fremde Init-Archive, zusätzliche Writer und physische MariaDB-Backups sind nicht abgenommen. |
| [CVE-2026-85091](https://ubuntu.com/security/CVE-2026-85091), `zlib1g` (1 MEDIUM) | Besondere nicht blockierende `gzwrite`-/`gzprintf`-Folge. Anders als die zuvor untersuchte JRE-`libzip.so` bindet `mariadbd` das OS-`libz.so.1` tatsächlich ein. Ubuntu dokumentiert Unsicherheit über betroffene Versionen und einen zurückgestellten Fix. | Keine pauschale Aussage zur Unerreichbarkeit. Kompression, Plugins und Verwaltungsprogramme sind bei geänderten Arbeitsabläufen gesondert zu prüfen; vollständige native Aufrufanalyse wurde nicht durchgeführt. |
| [CVE-2026-86145](https://ubuntu.com/security/CVE-2026-86145), [CVE-2026-89156](https://ubuntu.com/security/CVE-2026-89156), [CVE-2026-89157](https://ubuntu.com/security/CVE-2026-89157), [CVE-2026-89158](https://ubuntu.com/security/CVE-2026-89158), [CVE-2026-89160](https://ubuntu.com/security/CVE-2026-89160), [CVE-2026-89161](https://ubuntu.com/security/CVE-2026-89161), [CVE-2026-103111](https://ubuntu.com/security/CVE-2026-103111), `libpcre2-8-0`, `libpcre2-posix3` (14 MEDIUM, neu seit September) | Speicherfehler beim Übersetzen oder Ausführen angreiferkontrollierter regulärer Ausdrücke beziehungsweise ungültiger UTF-Eingaben in PCRE2. `ldd /usr/sbin/mariadbd` (19. Oktober 2026) bindet `libpcre2-8` für `REGEXP`/`RLIKE`. Der Anwendungscode enthält kein REGEXP; ein Angriff setzt eigenen SQL-Zugriff mit frei wählbaren Mustern voraus. Der Port ist im Referenz-Compose nicht veröffentlicht. | Betreiberentscheidung: **offen**. Aufgabe: bestätigen, dass außer der Anwendung keine SQL-Clients mit Nutzereingaben (Reporting, Ad-hoc-Abfragen) auf die Datenbank zugreifen. |
| [CVE-2026-67693](https://ubuntu.com/security/CVE-2026-67693), [CVE-2026-88647](https://ubuntu.com/security/CVE-2026-88647), [CVE-2026-95184](https://ubuntu.com/security/CVE-2026-95184), [CVE-2026-95209](https://ubuntu.com/security/CVE-2026-95209), [CVE-2026-95210](https://ubuntu.com/security/CVE-2026-95210), `libgnutls30t64` (5 MEDIUM, neu seit September) | Fehler der Zertifikatsprüfung in GnuTLS-Clients. `mariadbd` bindet OpenSSL (`libssl.so.3`), nicht GnuTLS; GnuTLS wird im Image nur von `apt` verwendet, das zur Laufzeit nicht läuft. | Betreiberentscheidung: **offen**; dokumentieren, dass im laufenden Container kein `apt` ausgeführt wird. |
| [CVE-2026-102473](https://ubuntu.com/security/CVE-2026-102473), [CVE-2026-102474](https://ubuntu.com/security/CVE-2026-102474), `dash` (2 MEDIUM, neu seit September) | Backtracking in `pmatch` und Heap-Schreibfehler in `conv_escape` bei gestalteten Shell-Eingaben. Hersteller-Entrypoint, Healthcheck und `init-database.sh` laufen mit festen Eingaben beziehungsweise Umgebungswerten des Betreibers. | Betreiberentscheidung: **offen**; eigene Betriebsskripte auf Nutzereingaben prüfen. |
| [CVE-2026-89092](https://ubuntu.com/security/CVE-2026-89092), `libc6`, `libc-bin` (2 MEDIUM, neu seit September) | Stapelüberlauf in `nscd`; der Dienst ist im Image weder installiert noch gestartet. | Betreiberentscheidung: **offen**. |
| [CVE-2025-29481](https://ubuntu.com/security/CVE-2025-29481), `libbpf1` (1 LOW) | Verarbeitung geeigneter ELF/BPF-Daten; die Sicherheitswirkung ist laut Ubuntu/upstream umstritten. Das Paket kommt über `iproute2`; `mariadbd` bindet es nicht direkt ein. | Keine fremden BPF-/ELF-Dateien mit administrativen Netzwerkwerkzeugen laden. Paket bleibt sichtbar; weder eine Streitnotiz noch fehlende direkte Verlinkung ist ein vollständiger Ausschluss. |
| [CVE-2025-1352](https://ubuntu.com/security/CVE-2025-1352), [CVE-2025-1376](https://ubuntu.com/security/CVE-2025-1376), `libelf1t64` (2 LOW) | Herstellerbeschreibungen betreffen ELF-Verarbeitung in `eu-readelf` beziehungsweise `eu-strip`. Diese beiden Programme fehlen im geprüften Image; `libelf` bleibt als Abhängigkeit der Netzwerkwerkzeuge vorhanden. | Fehlende Programme begrenzen die beschriebenen konkreten Aufrufwege, bewerten aber nicht sämtliche Bibliotheksnutzer. Zusätzliche Diagnosewerkzeuge und fremde ELF-Eingaben neu prüfen. |
| [CVE-2026-40228](https://ubuntu.com/security/CVE-2026-40228), `libsystemd0`, `libudev1` (2 LOW) | Voraussetzung ist `systemd-journald` mit `ForwardToWall`. Der Daemon fehlt im Image; die Bibliotheken sind dennoch vorhanden, `mariadbd` nutzt `libsystemd`. | Die konkrete Daemon-Voraussetzung liegt im geprüften Container nicht vor. Host-Logging und andere Images bleiben eigenständig zu bewerten. |
| [CVE-2024-56433](https://ubuntu.com/security/CVE-2024-56433), `login`, `passwd` (2 LOW) | Konflikte untergeordneter UID-Bereiche bei entsprechender Benutzer-/Netzwerkadministration. `newuidmap` fehlt im Image. | Host-User-Namespaces, NFS-/UID-Zuordnung und eigene administrative Änderungen prüfen. Die Capabilities-/No-new-privileges-Konfiguration des App-Containers ist keine Aussage über den Datenbankcontainer. |

Der MariaDB-Port wird im Referenz-Compose nicht auf dem Host veröffentlicht.
Die Laufzeit- und Migrationskonten erhalten durch `init-database.sh` nur die dort
aufgeführten Datenbankrechte, insbesondere kein globales `FILE` oder `SUPER`.
Das begrenzt Berechtigungen, beweist aber keine Unausnutzbarkeit nativer Fehler.
Das Rootkonto, Dockerzugang und schreibbarer Zugriff auf Konfiguration/Volumes
bleiben administrative Vertrauensgrenzen.

## Nachweise und Entscheidung

Geprüft wurden der installierte Paketbestand und seine Rückwärtsabhängigkeiten,
simulierte Entfernung, `ldd /usr/sbin/mariadbd`, Hersteller-Entrypoint/Healthcheck,
unsere Betriebsskripte sowie die konkret fehlenden Programme. Die laufenden
Entwicklungsdienste wurden dafür nicht verändert. Der Deploymenttest verlangt
die Abwesenheit von GPG/SQLite und die Verfügbarkeit von `gpgv`.
Ein frisches APT-Update mit strengem Fehlerabbruch und anschließender
Abhängigkeitsprüfung bestand in einem separaten entbehrlichen Container.
Die Paketquellen bieten für die zusätzlich geprüften `libc6`, `perl`,
`perl-base`, `zlib1g`, `libbpf1` und `libelf1t64` derzeit genau die installierten
Versionen an. Der endgültige Scannerbericht nennt für keinen Restbefund einen
verfügbaren Fix. Diese Beobachtung gilt für den Prüfzeitpunkt, nicht dauerhaft.

Die Prüfzahlen und Grenzen stehen in [R20 der Roadmap](roadmap.md). Vollständige
Berichte samt Image-ID bleiben in `target/database-image-audit*.json`.
Vor jedem Release Herstellerstatus und Scan aktualisieren. Bei einem verfügbaren
Fix Image neu bauen und die betroffenen Betriebs-/Recoverytests wiederholen;
HIGH/CRITICAL oder unvollständige Scans blockieren unverändert den Lieferprozess.

Betreiber, Entscheidung, Begründung und nächster Prüftermin sind im
[Abnahmeprotokoll](operational-acceptance.md) **noch festzulegen**. Diese Bewertung
erteilt keine Risikoakzeptanz und ersetzt keine gehostete CI, Lastmessung oder
Wiederherstellungsprobe mit dem tatsächlichen Datenbestand.
