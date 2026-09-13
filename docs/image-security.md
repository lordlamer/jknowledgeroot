# Sicherheitsprüfung der Containerimages

Der OSV-Scan für Maven und npm wird durch einen Scan der im Anwendungs- und
Datenbankimage installierten Betriebssystempakete ergänzt. Diese Prüfungen sind vor einer
Veröffentlichung erforderlich. Die Anwendung wird dabei nicht gestartet.

## Lokal ausführen

Nach Maven-Paketierung mit der aktuellen `build.revision`:

```sh
target/frontend/node/node scripts/build-container.mjs knowledgeroot:verified knowledgeroot:database-verified
target/frontend/node/node --test scripts/image-audit-policy.test.mjs
target/frontend/node/node scripts/audit-image.mjs knowledgeroot:verified
target/frontend/node/node scripts/audit-image.mjs --database knowledgeroot:database-verified
```

Unter Windows heißen die Node-Befehle `target/frontend/node/node.exe`.
Der Buildhelfer aktualisiert den Dateizeitpunkt des JARs auf dem Host, ohne dessen
Bytes zu verändern. Damit übernimmt Docker Desktop auch erneut gebaute Archive
mit gleicher Größe und normalisiertem Maven-Zeitpunkt zuverlässig in den Kontext.
Anschließend prüft `release-metadata.mjs` die Labels und die bytegleiche JAR-Kopie.
Ein Fehler lässt den Befehl scheitern; das Image gilt dann nicht als geprüft.

Der Scanner ist das offizielle Trivy-Image `0.74.0`, festgelegt auf
`sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969`.
Ein Versionswechsel benötigt eine erneute Prüfung von Herkunft, Digest und
Berichtsformat. Die Schwachstellendatenbank wird bei jedem Lauf frisch geladen;
dieser Download benötigt Internetzugang.

Das Skript löst den lokalen Imagenamen auf eine feste Image-ID auf und exportiert
genau dieses Image. Trivy erhält nur das lesbare Imagearchiv und ein eigenes
temporäres Ausgabeverzeichnis. Docker-Socket, Repository, lokale Env-Dateien und
Registry-Zugangsdaten werden nicht in den Scancontainer eingebunden. Nach Ende
wird ausschließlich das eigens erzeugte temporäre Verzeichnis entfernt.

## Ergebnis und Freigaberegel

- `target/image-audit.json`: vollständiger Trivy-Bericht mit Paketbestand und Funden.
- `target/image-audit-summary.json`: Ergebnis, blockierende Funde, Scanner-Digest,
  Image-ID/Labels, Archivprüfsumme, Scanzeit und Metadaten der Schwachstellendatenbank.
- `target/database-image-audit.json` und `target/database-image-audit-summary.json`:
  dieselben Nachweise für den Datenbankstandard aus dem Produktions-Compose.
  `target/frontend/node/node scripts/audit-image.mjs --database knowledgeroot:database-verified`
  scannt das ausdrücklich übergebene lokal gebaute Datenbankimage über dessen
  feste Image-ID. Es liest keine Betreiber-Env-Datei und wählt kein Ersatzimage.
  Eigene betriebliche Overrides separat scannen.
- Exitcode 0: vollständiger erkannter OS-Paketbestand ohne blockierende Funde.
- Exitcode ungleich 0: HIGH, CRITICAL oder unbekannter Schweregrad, nicht mehr
  unterstütztes OS, fehlender Paketbestand, inkompatibler Bericht oder technischer
  Fehler. Hohe Funde blockieren auch ohne verfügbaren Fix.

LOW und MEDIUM bleiben im Bericht sichtbar und sind betrieblich zu bewerten.
Es gibt keine automatische Ausnahme und keine übernommene `.trivyignore`.
Vor jedem neuen Scan werden alte Ergebnisdateien entfernt, damit ein fehlgeschlagener
Download keinen früheren Erfolg vortäuscht. Bei einem vollständigen Scan bleiben
die Berichte auch dann erhalten, wenn die Freigaberegel anschließend fehlschlägt.

Die CI führt Policy-Tests und Scan vor dem Publish-Job aus. Berichte werden auch
bei Fehlern als `container-os-audit` für 14 Tage abgelegt; bei Tags sind sie zusätzlich
Teil des geprüften Release-Artefakts. Diese Aufbewahrung ersetzt kein dauerhaftes
Release-Archiv. Scanfehler werden nicht mit `continue-on-error` übergangen.

## Grenzen

Geprüft werden OS-Pakete des Anwendungs- und Referenzdatenbankimages anhand der vom Scanner unterstützten
Herstellerdaten. Manuell installierte Laufzeiten wie die Temurin-JRE werden damit
nicht vollständig bewertet; ihre Version und Supportlage müssen weiter separat
gepflegt werden. Auch MariaDB-Pakete aus der Herstellerquelle sind mit einer
Ubuntu-OS-Prüfung nicht vollständig auf Serverlücken untersucht; die gepflegte
Serverversion und deren Herstellerhinweise bleiben eine eigene Prüfung.
Maven-/npm-Pakete bleiben beim OSV-Scan. Abweichende Datenbankimages, Proxyimages,
Hostbetriebssystem, Konfiguration, unbekannte Lücken und die konkrete Erreichbarkeit
eines verwundbaren Codes sind nicht durch diesen Scan abgenommen.

Bei blockierenden Funden zuerst Herstellerhinweis und gepinntes Basisimage prüfen,
das Image nach einer Korrektur neu bauen und erneut scannen. Anschließend die
betroffenen Start-/Betriebstests wiederholen. Nicht einfach Schweregrade herabsetzen
oder ungeprüfte Ausnahmen hinzufügen.

## Nachprüfung vom 13. September 2026

Der erste Scan des unveränderten Kandidaten zu Commit `cf013cb` erfasste 108
OS-Pakete mit 50 MEDIUM- und vier LOW-Paketbefunden, ohne HIGH/CRITICAL.
18 der mittleren Paketbefunde betreffen sechs glibc-CVEs in jeweils drei Paketen.
Ubuntu stellt dafür `2.39-0ubuntu8.9` bereit; das am selben Tag geprüfte aktuelle
Temurin-Tag zeigte noch auf den bereits verwendeten Digest mit `2.39-0ubuntu8.8`.

Der Dockerbuild aktualisiert deshalb gezielt `libc6`, `libc-bin` und `locales`
auf die festgelegte Ubuntu-Version. Die Paketquelle bleibt die signierte
Ubuntu-Paketverwaltung; zusätzliche Repositories werden nicht eingebunden.
Sobald ein geprüftes Temurin-Basisimage diese Korrektur enthält, kann die
zusätzliche Update-Schicht entfallen. Ein zukünftig nicht mehr verfügbares
Paket lässt den Build scheitern und erfordert ein geprüftes Basisimage-Update.

Die Hersteller-Schweregrade sind keine Aussage darüber, dass verbleibende Funde
für diese Anwendung unerreichbar wären. Die Betriebsabnahme muss diese weiterhin
bewerten; insbesondere ist ein bestandener Scan kein Nachweis der Lückenfreiheit.
Das Ergebnis des abschließenden Scans steht im R16-Nachweis der [Roadmap](roadmap.md).

R17 entfernt anschließend die ungenutzte System-Schriftverwaltung samt Expat.
Die [Bewertung der Containerbefunde](container-findings.md) dokumentiert diesen
Schritt und die Voraussetzungen der verbleibenden CVEs. Der Scanner prüft
weiterhin den tatsächlichen Paketbestand ohne Ausnahmen.

## Ausgangsbefunde (R18)

Der in R18 verwendete Standard `mariadb:12.3.3` mit Digest
`sha256:ab1c3dd381940233af12512b97d47b508fd3a0f17fbe3ba388739b7bc17cbc0b`
enthält laut Scan vom 13. September 2026 **151 OS-Pakete und 33 Paketbefunde**:
26 MEDIUM, sieben LOW, keine HIGH/CRITICAL/UNKNOWN. Die Schweregradregel besteht;
das ist noch keine betriebliche Freigabe. Zwölf mittlere Befunde in `libc6` und
`libc-bin` haben bereits einen Ubuntu-Fix in `2.39-0ubuntu8.9`. Dieser fehlt noch
im Herstellerimage. Die verbleibenden Befunde betreffen unter anderem Perl,
SQLite, tar, zlib und native Systembibliotheken. Die Bewertung des App-Images
in `container-findings.md` lässt sich nicht pauschal auf einen Datenbankprozess
übertragen. Die Korrektur ist im folgenden R19-Abschnitt beschrieben.

Die separate Java-Prüfung ergab außerdem das Wartungsrelease **25.0.4.1+1**;
das damalige App-Image verwendete **25.0.4+7**. Die
[Java-Releasehinweise](https://www.oracle.com/java/technologies/javase/25-0-4-1-relnotes.html)
nennen Sicherheitskorrekturen. Die offizielle Adoptium-API bietet die neue
Temurin-JRE als Archiv an; am Prüftag war das zugehörige Docker-Hub-Tag
`25.0.4.1_1-jre-noble` nicht verfügbar. Ein bestandener OS-Scan erledigt diese
Laufzeitprüfung nicht.

## Laufzeit- und Datenbankkorrekturen (R19)

Der Dockerbuild lädt das offizielle Temurin-JRE-Archiv für **25.0.4.1+1**, prüft
dessen im Dockerfile festgelegte SHA-256-Summe und ersetzt das gesamte alte
JRE-Verzeichnis einschließlich der zugehörigen `JAVA_VERSION`-Metadaten.
Downloadwerkzeuge bleiben in einer separaten Buildstufe;
das Laufzeitimage übernimmt ausschließlich das verifizierte JRE-Verzeichnis.
Für amd64 und arm64 sind eigene Herstellerarchive und Prüfsummen festgelegt;
andere Architekturen werden abgelehnt. Lokal erprobt wird Linux/amd64.
Sobald ein geprüftes offizielles Containerimage das Release enthält, kann die
zusätzliche Buildstufe durch diesen neuen Basis-Digest ersetzt werden.

`deploy/Dockerfile.database` baut auf dem unveränderten MariaDB-12.3.3-Digest
auf und aktualisiert ausschließlich `libc6` und `libc-bin` auf
`2.39-0ubuntu8.9`. Datenbankserver und Entrypoint bleiben erhalten. Ein erster
erneuter Scan erfasst weiterhin **151 Pakete**, jetzt **14 MEDIUM und sieben
LOW**, ohne HIGH/CRITICAL/UNKNOWN. Alle zwölf zuvor behebbaren glibc-Paketbefunde
entfallen; für die 21 Restbefunde nennt der Bericht keine verfügbare Korrektur.
Die verbleibenden Voraussetzungen sind für den Datenbankbetrieb gesondert zu
bewerten; die App-Einschätzung ist keine Freigabe für den Datenbankprozess.

App und Datenbank werden gemeinsam gebaut und getestet. Das Release-Manifest
verlangt übereinstimmende Versions-/Revisionslabels beider Images und prüft die
JRE-, MariaDB- und glibc-Versionen. Die CI transportiert beide geprüften Images
gemeinsam zum Publish-Job. `KR_DB_IMAGE` ist nun verpflichtend; ein stiller
Rückfall auf das ungepatchte Herstellerimage ist ausgeschlossen. Eigene
Overrides sind weiterhin möglich und benötigen eine eigene Prüfung.

Der lokale Maven-Lauf verwendet das offizielle Windows-JDK mit ebenfalls
geprüfter SHA-256-Summe. In `actions/setup-java` wird die Adoptium-SemVer
`25.0.4+101.0.LTS` verwendet, die die API für **25.0.4.1+1-LTS** liefert.
Zusätzlich prüft die CI `JAVA_RUNTIME_VERSION` im installierten JDK, bevor
Maven läuft. Künftige Wartungsreleases erfordern ein gemeinsames Update dieser
Vorgaben und der Artefaktprüfung. Die endgültigen Testergebnisse und Imagescans
stehen im R19-Nachweis der [Roadmap](roadmap.md).

## Datenbankbewertung und entbehrliche Pakete (R20)

Die [gesonderte Datenbankbewertung](database-findings.md) untersucht die
MariaDB-Paketabhängigkeiten, native Verlinkung sowie Start-, Wartungs- und
Wiederherstellungswege. Der Build entfernt die ungenutzte SQLite-Bibliothek
zusammen mit ihrem einzigen installierten Verbraucher `gpg`. `gpgv`, Schlüssel
und die übrigen MariaDB-Abhängigkeiten bleiben erhalten; der Deploymenttest
prüft die Entfernung. Eigene GPG-/SQLite-Initialisierungsskripte gehören nicht
zum geprüften Betrieb. Die Bewertung erteilt keine Risikoakzeptanz und verwendet
keine Scanner-Ausnahmen. Der endgültige Scan steht im R20-Roadmapnachweis.

Quellen: [Adoptium JRE-Artefakte für Linux x64](https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jre&os=linux&vendor=eclipse),
[Trivy v0.74.0](https://github.com/aquasecurity/trivy/releases/tag/v0.74.0),
[Imagearchive scannen](https://trivy.dev/docs/latest/target/container_image/),
[OS-Pakete und Herstellerbewertungen](https://trivy.dev/docs/dev/guide/scanner/vulnerability/),
[Ubuntu: glibc-Korrektur für Noble](https://ubuntu.com/security/CVE-2026-19499).
