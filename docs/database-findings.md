# Bewertung der Datenbank-Containerbefunde

Stand: 13. September 2026, R20. Diese technische Bewertung betrifft das gemeinsam
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
| [CVE-2026-15534](https://ubuntu.com/security/CVE-2026-15534), [CVE-2026-19487](https://ubuntu.com/security/CVE-2026-19487), `perl`, `perl-base`, `perl-modules-5.38`, `libperl5.38t64` (8 MEDIUM) | Fehler in Perls Regex-Verarbeitung. Perl ist eine echte Paketabhängigkeit; unser Entrypoint-/Healthcheck-/Recoveryablauf enthält keine direkte Perl-Verarbeitung von Nutzereingaben. `mariadbd` bindet für Regex-Verarbeitung `libpcre2-8` ein. | Daraus folgt kein Beweis für alle mitgelieferten Verwaltungsprogramme. Fremde Logs/Eingaben nicht ungeprüft mit solchen Werkzeugen verarbeiten; eigene Wartungsskripte und Plugins prüfen. Der aktuelle Noble-Stand ist von Fixes anderer Ubuntu-Reihen zu unterscheiden. |
| [CVE-2026-18477](https://ubuntu.com/security/CVE-2026-18477), [CVE-2026-18508](https://ubuntu.com/security/CVE-2026-18508), `tar` (2 MEDIUM) | Betreffen inkrementelle Dumpdir-Wiederherstellung beziehungsweise `--one-top-level`. Unser Datenbankbackup/-restore nutzt SQL; Datei-Vollarchive werden im App-Container verarbeitet. Der MariaDB-Entrypoint unterstützt zusätzlich andere Archiv-Initialisierungen, die unser Compose nicht verwendet. | Nur eigene geschützte Sicherungen und die dokumentierte SQL-Wiederherstellung zulassen. Fremde Init-Archive, zusätzliche Writer und physische MariaDB-Backups sind nicht abgenommen. |
| [CVE-2026-85091](https://ubuntu.com/security/CVE-2026-85091), `zlib1g` (1 MEDIUM) | Besondere nicht blockierende `gzwrite`-/`gzprintf`-Folge. Anders als die zuvor untersuchte JRE-`libzip.so` bindet `mariadbd` das OS-`libz.so.1` tatsächlich ein. Ubuntu dokumentiert Unsicherheit über betroffene Versionen und einen zurückgestellten Fix. | Keine pauschale Aussage zur Unerreichbarkeit. Kompression, Plugins und Verwaltungsprogramme sind bei geänderten Arbeitsabläufen gesondert zu prüfen; vollständige native Aufrufanalyse wurde nicht durchgeführt. |
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
