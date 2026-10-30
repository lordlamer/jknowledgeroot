# Bewertung der verbleibenden Containerbefunde

Stand: 13. September 2026, R17; ergänzt am 19. Oktober 2026 (R38) und am 30. Oktober 2026 (R45: `libbrotli1`-HIGH und `zlib1g`-Befund durch das neue Basisimage behoben) auf Basis des Scans mit 23 MEDIUM und vier LOW,
siehe [image-security.md](image-security.md). Diese technische Bewertung ist eine Grundlage für
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
verbleibenden Paketbefunde vom 13. September entsprachen acht CVEs; der Scan vom 19. Oktober 2026 zählt 27 Paketbefunde zu 21 CVEs, alle ohne verfügbaren Fix. Die folgenden
Einschätzungen trennen Herstellerbeschreibung und lokal geprüfte Voraussetzungen.

| CVE / Pakete | Herstellervoraussetzung | Prüfung am Projekt / verbleibende Aufgabe |
| --- | --- | --- |
| [CVE-2026-18374](https://ubuntu.com/security/CVE-2026-18374), `libc6`, `libc-bin`, `locales` (MEDIUM) | Angreiferkontrollierter Modusstring mit problematischem `,ccs=`-Wert für glibc `fopen`; Ubuntu meldet den Fix als zurückgestellt. | Keine native `fopen`-Schnittstelle oder Übernahme solcher Modusstrings im Anwendungscode gefunden. Das ist kein vollständiger Nachweis für alle internen JRE-Pfade. Hersteller beobachten und aktualisieren, sobald ein Fix verfügbar ist. |
| [CVE-2026-15534](https://ubuntu.com/security/CVE-2026-15534) und [CVE-2026-19487](https://ubuntu.com/security/CVE-2026-19487), `perl-base` (MEDIUM) | Fehler in Perls Regex-Verarbeitung bei bestimmten Mustern und Eingaben; für Noble aktuell kein Fix im Scan. | Keine Prozessaufrufe oder Perl-Verarbeitung von HTTP-/Dateieingaben im Servercode oder den Betriebsskripten gefunden. `perl-base` gehört zur Basis/Paketverwaltung und wird nicht gewaltsam aus deren Abhängigkeiten entfernt. Bei neuen Skripten oder Plugins erneut prüfen. **Behoben:** Der Temurin-Digest vom 17. Oktober 2026 enthält `perl-base 5.38.2-3.2ubuntu0.6`; der Befund ist seit R38 nicht mehr im Scan. |
| [CVE-2026-18477](https://ubuntu.com/security/CVE-2026-18477), `tar` (MEDIUM) | Rennen bei der Wiederherstellung inkrementeller GNU-tar-Dumpdir-Einträge unter lokalem Schreibzugriff. Ein leeres Ziel allein genügt laut Hersteller nicht als Schutz. | `recovery.sh` erstellt Vollarchive ohne inkrementellen Modus. Vorgesehen sind ausschließlich eigene geschützte Backups, gestoppte Writer und ein separates leeres Ziel. Andere Archive oder lokale Mitbenutzer mit Schreibzugriff sind damit nicht abgenommen. Restoreprozess und Zielzugriff sind durch den Betreiber zu bestätigen. |
| [CVE-2026-18508](https://ubuntu.com/security/CVE-2026-18508), `tar` (MEDIUM) | Hardlink-Ausbruch bei `--one-top-level` mit geeignetem Archiv und vorhandener Symlink-Konstellation. | Diese Option wird in keinem Backup-/Restore-Aufruf verwendet. Die Prüfsumme ist weiterhin kein Herkunftsnachweis für ein fremdes Archiv; nur eigene, geschützte Backups zulassen. |
| [CVE-2026-85091](https://ubuntu.com/security/CVE-2026-85091), `zlib1g` (MEDIUM) | Seit R45 behoben: Das Basisimage vom 27. Oktober 2026 enthält `zlib1g 1:1.3.dfsg-3.1ubuntu2.3`; der Scan meldet den Befund nicht mehr. | Keine Entscheidung mehr nötig. |
| [CVE-2026-86145](https://ubuntu.com/security/CVE-2026-86145), [CVE-2026-89156](https://ubuntu.com/security/CVE-2026-89156), [CVE-2026-89157](https://ubuntu.com/security/CVE-2026-89157), [CVE-2026-89158](https://ubuntu.com/security/CVE-2026-89158), [CVE-2026-89160](https://ubuntu.com/security/CVE-2026-89160), [CVE-2026-89161](https://ubuntu.com/security/CVE-2026-89161), [CVE-2026-103111](https://ubuntu.com/security/CVE-2026-103111), `libpcre2-8-0` (7 MEDIUM, neu seit September) | Speicherfehler beim Übersetzen oder Ausführen angreiferkontrollierter regulärer Ausdrücke beziehungsweise ungültiger UTF-Eingaben in PCRE2, teils nur im JIT oder auf 32-Bit-Plattformen; Ubuntu nennt keinen Fix. | `ldd` am gebauten Image (19. Oktober 2026): Weder `java` noch eine JRE-Bibliothek bindet `libpcre2-8`; die Anwendung verwendet `java.util.regex`. Nutzer sind ausschließlich Systemwerkzeuge wie `chsh`, `chcon` und `grep`, die im Container nicht mit Nutzereingaben laufen. Betreiberentscheidung: **offen**. |
| [CVE-2026-67693](https://ubuntu.com/security/CVE-2026-67693), [CVE-2026-88647](https://ubuntu.com/security/CVE-2026-88647), [CVE-2026-95184](https://ubuntu.com/security/CVE-2026-95184), [CVE-2026-95209](https://ubuntu.com/security/CVE-2026-95209), [CVE-2026-95210](https://ubuntu.com/security/CVE-2026-95210), `libgnutls30t64` (5 MEDIUM, neu seit September) | Fehler der Zertifikatsprüfung in GnuTLS-Clients, etwa Hostnamenprüfung und Key-Usage-Auswertung; Ubuntu nennt keinen Fix. | Kein Prozess des Images außer `apt` bindet GnuTLS; Java verwendet JSSE für TLS, `apt` läuft zur Laufzeit nicht. Betreiberentscheidung: **offen**; Aufgabe: bestätigen, dass im laufenden Container kein `apt` ausgeführt wird. |
| [CVE-2026-102473](https://ubuntu.com/security/CVE-2026-102473), [CVE-2026-102474](https://ubuntu.com/security/CVE-2026-102474), `dash` (2 MEDIUM, neu seit September) | Superpolynomielles Backtracking in `pmatch` und Heap-Schreibfehler in `conv_escape` bei entsprechend gestalteten Shell-Mustern beziehungsweise Escape-Sequenzen. | `sh` wird nur für den Hersteller-Entrypoint und den Healthcheck mit festen Eingaben verwendet; die Anwendung startet keine Shell mit Nutzereingaben. Betreiberentscheidung: **offen**; Aufgabe: eigene Betriebsskripte auf Nutzereingaben prüfen. |
| [CVE-2026-89092](https://ubuntu.com/security/CVE-2026-89092), `libc6`, `libc-bin`, `locales` (MEDIUM, neu seit September) | Stapelüberlauf in `nscd` mit degradierter DNS-Auflösung. | `nscd` ist im Image weder installiert noch gestartet. Betreiberentscheidung: **offen**. |
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
