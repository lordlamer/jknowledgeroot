# Release-Kandidat und Freigabe

Der vorbereitete Stand heißt **1.0.0-rc.1**. Er ist noch keine Produktionsfreigabe.
Es wurde weder ein Release-Tag erstellt noch ein Image veröffentlicht. R15,
produktive Lastmessung und die Abnahme der tatsächlichen Betriebsumgebung bleiben
offen. Backup, Upgrade und Rückkehr beschreibt [recovery.md](recovery.md).

## Artefakte zuordnen

Der Maven-Build erzeugt `knowledgeroot-1.0.0-rc.1.jar` und eingebettete Builddaten
einschließlich Version und optional übergebener Revision. Beispiel unter Bash:

```sh
revision=$(git rev-parse HEAD)
sh ./mvnw -B --no-transfer-progress -Dbuild.revision="$revision" clean verify
version=$(target/frontend/node/node scripts/release-metadata.mjs --version)
docker build --build-arg BUILD_VERSION="$version" --build-arg BUILD_REVISION="$revision" --tag knowledgeroot:verified .
target/frontend/node/node scripts/release-metadata.mjs knowledgeroot:verified
```

`target/release-manifest.json` nennt Maven-Version, Git-Revision, Änderungen an
versionierten Dateien, JAR-SHA-256, Image-ID und Basisimage. Unter Windows kann
`target/frontend/node/node.exe` verwendet werden. Lokale Arbeitskopien mit Änderungen
sind als solche erkennbar und keine Release-Artefakte. Ohne `build.revision` tragen
die JAR-Builddaten `unknown`; für freigegebene Artefakte die Revision immer mitgeben.

Die festgelegte Maven-Ausgabezeit reduziert zeitbedingte Archivunterschiede. Eine
garantierte bitgleiche Reproduktion über beliebige Betriebssysteme oder JDK-Builds
wird damit nicht behauptet. Maßgeblich sind Prüfsummen der tatsächlich getesteten
Artefakte. Die CI veröffentlicht exakt das geprüfte Image aus ihrem Artefakt,
einschließlich Versions-/Commit-Tags; sie baut es im Publish-Job nicht erneut.

## Freigabeschritte

1. Sauberen Release-Commit auf `master` vorbereiten. Kandidatenversion in der POM
   und Docker-Standardlabel gemeinsam pflegen. Bei einem Versionswechsel alte
   JARs durch `clean` entfernen, damit das Docker-COPY eindeutig bleibt.
2. Gesamten Maven-Testlauf, Containerstart und Backup-/Upgrade-/Rollbackprüfung
   bestehen lassen. Dependency-Baum und OSV-Bericht archivieren. Der Workflow führt
   den vorhandenen Scanner nun aus und stoppt bei Funden oder unvollständiger
   Prüfung. OSV erfasst Maven-/npm-Pakete; ein vollständiger Container-OS-Scan und
   organisationsspezifische Release-Regeln sind gesondert zu erfüllen.
3. In der Zielumgebung TLS/Proxy, Rechte, Last und Loginquoten, Speicher-/Backupplatz,
   externe Alarmierung und Restore-Zeiten prüfen. Repräsentative eigene Altinhalte
   im Editor kontrollieren. R15 muss vor breitem Mehrbenutzerbetrieb erledigt sein.
4. Repository-Schutz und GitHub-Umgebung `release` gemäß [production.md](production.md)
   einrichten. Erst nach Freigabe den passenden Tag, beispielsweise
   `v1.0.0-rc.1`, erstellen. Ein abweichender Tag und eine veränderte Arbeitskopie
   werden bei der Artefaktprüfung abgelehnt. Ein Kandidat ist kein endgültiges
   `v1.0.0`-Release.
5. Image, Release-Manifest, Scanbericht und Betriebsnachweise dauerhaft archivieren.
   Die dreitägige CI-Artefaktaufbewahrung ist kein Release-Archiv. Den veröffentlichten
   Registry-Digest zusätzlich festhalten und im Deployment referenzieren.

Die automatisierte Prüfung kann lokale und technische Nachweise liefern. Domain,
Registry-Veröffentlichung, externe Freigaben und betriebliche Schwellenwerte werden
durch diesen Arbeitsstand nicht stellvertretend eingerichtet.
