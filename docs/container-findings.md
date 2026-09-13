# Bewertung der verbleibenden Containerbefunde

Stand: 13. September 2026, R17. Diese technische Bewertung ist eine Grundlage für
die Betriebsabnahme und keine erteilte Risikoakzeptanz. Herstellerstatus und
Paketbestand beziehen sich auf diesen Zeitpunkt und das geprüfte App-Image.
Die vollständigen Scannerberichte bleiben unverändert; es gibt keine Ignore-Liste.
Die eigenständige [Datenbankbewertung ab R20](database-findings.md) untersucht
die zusätzlichen Pakete und Aufrufwege des MariaDB-Containers.

## Entfernte Pakete

Im R16-Image stammen 24 der 36 Paketbefunde aus `libexpat1`. Die installierte
Paketabhängigkeit führt ausschließlich über `libfontconfig1` zur Schriftverwaltung
`fontconfig`. Der Servercode verwendet weder AWT/ImageIO noch PDF-/Schriftrendering;
Seiten, Editor und Bilder werden im Browser dargestellt. Uploads werden als Bytes
gespeichert und ausgeliefert.

R17 entfernt gezielt `fontconfig`, `libfontconfig1` und `libexpat1` über die
Paketverwaltung. Der simulierte Paketplan entfernte genau diese drei Pakete;
`autoremove` wird nicht verwendet. Die übrigen JRE-/Basisabhängigkeiten bleiben
erhalten. Der Deploymenttest prüft zusätzlich, dass die drei Pakete fehlen.

Das Image ist damit für den vorhandenen Webserver ausgelegt. Eine spätere
serverseitige PDF-, Schrift- oder Bildrenderfunktion benötigt eine neue Prüfung
der nativen Abhängigkeiten und passende Tests. Browser-Schriften und
Dateidownloads verwenden die entfernte System-Schriftverwaltung nicht.

Für Expat meldet Ubuntu weiterhin betroffene Noble-Pakete, beispielsweise
[CVE-2026-56408](https://ubuntu.com/security/CVE-2026-56408). Die Reduktion entfernt
hier die betroffene Bibliothek aus dem endgültigen Dateisystem; sie behauptet
keinen noch nicht gelieferten Herstellerfix.

## Befunde im verbleibenden Paketbestand

Ein CVE kann mehrere Binärpakete desselben Quellpakets betreffen. Die zwölf
verbleibenden Paketbefunde entsprechen acht unterschiedlichen CVEs. Die folgenden
Einschätzungen trennen Herstellerbeschreibung und lokal geprüfte Voraussetzungen.

| CVE / Pakete | Herstellervoraussetzung | Prüfung am Projekt / verbleibende Aufgabe |
| --- | --- | --- |
| [CVE-2026-18374](https://ubuntu.com/security/CVE-2026-18374), `libc6`, `libc-bin`, `locales` (MEDIUM) | Angreiferkontrollierter Modusstring mit problematischem `,ccs=`-Wert für glibc `fopen`; Ubuntu meldet den Fix als zurückgestellt. | Keine native `fopen`-Schnittstelle oder Übernahme solcher Modusstrings im Anwendungscode gefunden. Das ist kein vollständiger Nachweis für alle internen JRE-Pfade. Hersteller beobachten und aktualisieren, sobald ein Fix verfügbar ist. |
| [CVE-2026-15534](https://ubuntu.com/security/CVE-2026-15534) und [CVE-2026-19487](https://ubuntu.com/security/CVE-2026-19487), `perl-base` (MEDIUM) | Fehler in Perls Regex-Verarbeitung bei bestimmten Mustern und Eingaben; für Noble aktuell kein Fix im Scan. | Keine Prozessaufrufe oder Perl-Verarbeitung von HTTP-/Dateieingaben im Servercode oder den Betriebsskripten gefunden. `perl-base` gehört zur Basis/Paketverwaltung und wird nicht gewaltsam aus deren Abhängigkeiten entfernt. Bei neuen Skripten oder Plugins erneut prüfen. |
| [CVE-2026-18477](https://ubuntu.com/security/CVE-2026-18477), `tar` (MEDIUM) | Rennen bei der Wiederherstellung inkrementeller GNU-tar-Dumpdir-Einträge unter lokalem Schreibzugriff. Ein leeres Ziel allein genügt laut Hersteller nicht als Schutz. | `recovery.sh` erstellt Vollarchive ohne inkrementellen Modus. Vorgesehen sind ausschließlich eigene geschützte Backups, gestoppte Writer und ein separates leeres Ziel. Andere Archive oder lokale Mitbenutzer mit Schreibzugriff sind damit nicht abgenommen. Restoreprozess und Zielzugriff sind durch den Betreiber zu bestätigen. |
| [CVE-2026-18508](https://ubuntu.com/security/CVE-2026-18508), `tar` (MEDIUM) | Hardlink-Ausbruch bei `--one-top-level` mit geeignetem Archiv und vorhandener Symlink-Konstellation. | Diese Option wird in keinem Backup-/Restore-Aufruf verwendet. Die Prüfsumme ist weiterhin kein Herkunftsnachweis für ein fremdes Archiv; nur eigene, geschützte Backups zulassen. |
| [CVE-2026-85091](https://ubuntu.com/security/CVE-2026-85091), `zlib1g` (MEDIUM) | Besondere nicht blockierende `gzwrite`-/`gzprintf`-Folge mit veralteten Pufferzeigern; Ubuntu stuft den Fix als zurückgestellt ein. | Kein solcher nativer Aufruf im Anwendungscode. `ldd` zeigt für die verwendete JRE-`libzip.so` keine dynamische Abhängigkeit auf das OS-`libz`; daraus folgt keine allgemeine Bewertung gebündelter JRE-Bibliotheken. JRE und OS-zlib weiter getrennt beobachten. |
| [CVE-2026-40228](https://ubuntu.com/security/CVE-2026-40228), `libsystemd0`, `libudev1` (LOW) | `systemd-journald` mit aktivem `ForwardToWall` kann Terminal-Steuersequenzen weiterreichen. | Das geprüfte Image enthält keinen `systemd-journald`-Daemon und startet Java direkt. Die konkrete Daemon-Voraussetzung liegt für diesen Container nicht vor. Das beurteilt nicht das Hostsystem oder ein anderes Image. |
| [CVE-2024-56433](https://ubuntu.com/security/CVE-2024-56433), `login`, `passwd` (LOW) | Konflikte untergeordneter UID-Bereiche mit lokalen/Netzwerkidentitäten bei entsprechender Administration. | `newuidmap` fehlt im Image; die App läuft als UID/GID 10001 ohne zusätzliche Capabilities und ohne Privilegienausweitung. Geänderte User-Namespace-/NFS-/Host-Konfigurationen sind gesondert zu prüfen. |

Die statische Suche umfasst `src/main/java` und die ausgelieferten Betriebsskripte.
Sie ersetzt keine vollständige Analyse sämtlicher transitiver Java-/JNI-Pfade.
Die Aussagen zu nicht vorhandenen Programmen wurden am gebauten Container geprüft.
Maven-/npm- und OS-Scanregeln bleiben bestehen; diese Tabelle erzeugt keine
automatische Freigabe und keinen Scanner-Unterdrückungseintrag.

## Entscheidung und Nachverfolgung

- Betreiber und Vertretung für die Restbewertung: **offen**.
- Risikoentscheidung für die tatsächliche Umgebung: **nicht erteilt**.
- Herstellerstatus und neue Scans spätestens vor Tag/Veröffentlichung erneut prüfen;
  danach einen regelmäßigen betrieblichen Termin festlegen.
- Neue native Verarbeitung, serverseitiges Rendering, Plugins, geänderte
  Backupformate oder zusätzliche Host-/Volume-Rechte erfordern eine erneute Bewertung.
- Bei verfügbarem Fix: kontrolliertes Imageupdate, erneuter Scan und betroffene
  Betriebs-/Recoverytests. Bei neuem HIGH/CRITICAL-Fund stoppt die vorhandene CI-Regel.

Die genaue Image-ID, Scanzeit und Datenbankversion stehen in
`target/image-audit-summary.json`; Prüfzahlen im R17-Nachweis der
[Roadmap](roadmap.md). Die Entscheidung gehört anschließend in das ausgefüllte
[Betriebsabnahmeprotokoll](operational-acceptance.md).
