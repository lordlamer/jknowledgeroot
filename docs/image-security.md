# Sicherheitsprüfung des Anwendungsimages

Der OSV-Scan für Maven und npm wird durch einen Scan der im Anwendungsimage
installierten Betriebssystempakete ergänzt. Beide Prüfungen sind vor einer
Veröffentlichung erforderlich. Die Anwendung wird dabei nicht gestartet.

## Lokal ausführen

Nach Maven-Paketierung mit der aktuellen `build.revision`:

```sh
target/frontend/node/node scripts/build-container.mjs knowledgeroot:verified
target/frontend/node/node --test scripts/image-audit-policy.test.mjs
target/frontend/node/node scripts/audit-image.mjs knowledgeroot:verified
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

Geprüft werden OS-Pakete des Anwendungsimages anhand der vom Scanner unterstützten
Herstellerdaten. Manuell installierte Laufzeiten wie die Temurin-JRE werden damit
nicht vollständig bewertet; ihre Version und Supportlage müssen weiter separat
gepflegt werden. Maven-/npm-Pakete bleiben beim OSV-Scan. Datenbank-/Proxyimages,
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

Quellen: [Trivy v0.74.0](https://github.com/aquasecurity/trivy/releases/tag/v0.74.0),
[Imagearchive scannen](https://trivy.dev/docs/latest/target/container_image/),
[OS-Pakete und Herstellerbewertungen](https://trivy.dev/docs/dev/guide/scanner/vulnerability/),
[Ubuntu: glibc-Korrektur für Noble](https://ubuntu.com/security/CVE-2026-19499).
