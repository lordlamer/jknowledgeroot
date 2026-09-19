# Release-Kandidat und Freigabe

Der vorbereitete Stand heißt **1.0.0-rc.2**. Er ist noch keine Produktionsfreigabe.
Es wurde weder ein Release-Tag erstellt noch ein Image veröffentlicht.
Produktive Lastmessung und die Abnahme der tatsächlichen Betriebsumgebung bleiben
offen. Backup, Upgrade und Rückkehr beschreibt [recovery.md](recovery.md).

## Artefakte zuordnen

Der Maven-Build erzeugt `knowledgeroot-1.0.0-rc.2.jar` und eingebettete Builddaten
einschließlich Version und optional übergebener Revision. Für den Build Temurin
JDK **25.0.4.1+1** verwenden und `JAVA_HOME` darauf setzen. Beispiel unter Bash:

```sh
revision=$(git rev-parse HEAD)
sh ./mvnw -B --no-transfer-progress -Dbuild.revision="$revision" clean verify
target/frontend/node/node scripts/build-container.mjs knowledgeroot:verified knowledgeroot:database-verified
target/frontend/node/node scripts/audit-image.mjs knowledgeroot:verified
target/frontend/node/node scripts/audit-image.mjs --database knowledgeroot:database-verified
target/frontend/node/node scripts/release-metadata.mjs knowledgeroot:verified knowledgeroot:database-verified --with-audits
```

`target/release-manifest.json` nennt Maven-Version, Git-Revision, Änderungen an
versionierten Dateien, JAR-SHA-256, beide Image-IDs/Basisimages, Java-Laufzeit,
MariaDB-Serverversion und die installierten glibc-Versionen. Die beiden Images
müssen dieselbe Projektversion und Git-Revision tragen. Fehlende Laufzeitkorrekturen
brechen die Prüfung ab; alte Manifeste werden vor Build/Validierung entfernt.
Die eingebettete JAR-Version und -Revision müssen bereits vor dem Containerbuild
mit POM und aktuellem Commit übereinstimmen. Ein altes JAR lässt sich dadurch
nicht mit neuen Image-Labels als aktueller Build ausgeben.

Der Buildhelfer erzeugt zunächst ein vorläufiges Manifest mit
`auditsVerified: false`. Erst der abschließende Aufruf mit `--with-audits` prüft
beide vollständigen OS-Scanberichte gegen die jeweiligen Images: Image-ID,
Dateisystemschichten, Labels, festgelegter Scanner und Berichtsprüfsummen müssen
übereinstimmen. Die Schweregradregel wird aus den Rohberichten erneut ausgewertet;
abweichende Zusammenfassungen werden abgelehnt. Das abschließende Manifest enthält
`auditsVerified: true` sowie SHA-256-Werte beider Berichte und Zusammenfassungen.
Bei gesetztem `RELEASE_TAG` sind diese Prüfungen auch ohne Zusatzoption zwingend.
Die CI führt sie nach den Scans und vor dem gemeinsamen Imageexport aus.

Unter Windows kann
`target/frontend/node/node.exe` verwendet werden. Lokale Arbeitskopien mit Änderungen
sind als solche erkennbar und keine Release-Artefakte. Ohne `build.revision` tragen
die JAR-Builddaten `unknown`; der Containerbuild lehnt solche Artefakte ab.

Diese Zuordnung verhindert verwechselte oder widersprüchliche Nachweise. Sie ist
keine signierte Herkunftsbescheinigung und keine automatische Betriebsfreigabe.
Ein älterer Scan desselben Images wird dadurch nicht aktualisiert; vor der Freigabe
die oben genannten Scans erneut ausführen und ihre Datenbankstände mit archivieren.

Die festgelegte Maven-Ausgabezeit reduziert zeitbedingte Archivunterschiede. Eine
garantierte bitgleiche Reproduktion über beliebige Betriebssysteme oder JDK-Builds
wird damit nicht behauptet. Maßgeblich sind Prüfsummen der tatsächlich getesteten
Artefakte. Die CI veröffentlicht exakt die beiden geprüften Images aus ihrem
gemeinsamen Artefakt; sie baut sie im Publish-Job nicht erneut. Im vorhandenen
Repository `lordlamer/knowledgeroot` heißen die Tags für die Anwendung
`v1.0.0-rc.2` beziehungsweise `sha-<Commit>` und für die Datenbank
`database-v1.0.0-rc.2` beziehungsweise `database-sha-<Commit>`. Das sind vorbereitete
Namenskonventionen; veröffentlicht wurde noch nichts. Für das Deployment beide
Registry-Digests festhalten und explizit als `KR_APP_IMAGE` und `KR_DB_IMAGE` setzen.

## Freigabeschritte

1. Sauberen Release-Commit auf `master` vorbereiten. Kandidatenversion in der POM
   und die Standardlabels beider Dockerfiles gemeinsam pflegen. Bei einem Versionswechsel alte
   JARs durch `clean` entfernen, damit das Docker-COPY eindeutig bleibt.
2. Gesamten Maven-Testlauf, Containerstart und Backup-/Upgrade-/Rollbackprüfung
   sowie den [HTTPS-Proxytest](proxy-testing.md) bestehen lassen. Vor der Freigabe die Java-/Datenbank-OS-Korrekturen aus
   [R19](roadmap.md#r19--java-wartungsrelease-und-verfügbare-datenbank-os-fixes-übernehmen)
   abschließen. Dependency-Baum und OSV-Bericht archivieren. Der Workflow führt
   den vorhandenen Scanner nun aus und stoppt bei Funden oder unvollständiger
   Prüfung. OSV erfasst Maven-/npm-Pakete; der zusätzliche
   [OS-Imagescan](image-security.md) prüft Betriebssystempakete im App-Container
   und im gepinnten Produktions-Datenbankimage.
   Auf `master` aktualisiert der nachgelagerte Job `dependency-graph` außerdem
   GitHubs Maven-Abhängigkeiten aus genau diesem geprüften Build. Sein Artefakt
   `verified-dependencies` enthält Baum, Snapshot und OSV-Bericht (14 Tage).
   Vor der Freigabe auch diesen Job und die verbleibenden Dependabot-Meldungen
   prüfen. Bei einer übersprungenen Übermittlung wegen eines neueren Commits
   zählt der nachfolgende Lauf für den aktuellen Hauptbranch. Pull Requests und
   Tags übermitteln keinen Snapshot. Die Kennungen `build` und
   `maven-dependency-tree-action` sind absichtlich mit der früheren Quelle
   identisch; eine Umbenennung kann alte Abhängigkeiten im Graphen zurücklassen.
   Verbleibende Funde, Datenbankserver-/JRE-Laufzeit, Proxyimages und organisationsspezifische
   Release-Regeln sind gesondert zu bewerten.
3. In der Zielumgebung TLS/Proxy, Rechte, Last und Loginquoten, Speicher-/Backupplatz,
   externe Alarmierung und Restore-Zeiten prüfen. Repräsentative eigene Altinhalte
   im Editor kontrollieren. Versionskonflikte und Wiederherstellung gemäß
   [page-history.md](page-history.md) auch mit eigenen Arbeitsabläufen abnehmen.
   Dafür das [Abnahmeprotokoll](operational-acceptance.md) ausfüllen und archivieren.
   Passwortwechsel mit mehreren Sitzungen, Verschieben einschließlich vererbter
   Freigaben und Versionsvergleiche gemäß [product-functions.md](product-functions.md)
   gehören ebenfalls zur Abnahme; diese Funktionen sind seit R23–R25 enthalten.
4. Repository-Schutz und GitHub-Umgebung `release` gemäß
   [repository-protection.md](repository-protection.md) vervollständigen.
   Die technische Prüfung verlangt konfigurierte Reviewer und ausschließlich
   die Tag-Regel `v*`; bei fehlender Umgebung oder nicht prüfbarer API bricht
   sie vor dem Release-Build beziehungsweise Registry-Login ab.
   Erst nach Freigabe den passenden Tag, beispielsweise
   `v1.0.0-rc.2`, erstellen. Ein abweichender Tag und eine veränderte Arbeitskopie
   werden bei der Artefaktprüfung abgelehnt. Ein Kandidat ist kein endgültiges
   `v1.0.0`-Release.
5. Beide Images, Release-Manifest, Scanberichte und Betriebsnachweise dauerhaft archivieren.
   Die dreitägige CI-Artefaktaufbewahrung ist kein Release-Archiv. Den veröffentlichten
   Registry-Digest zusätzlich festhalten und im Deployment referenzieren.

Die automatisierte Prüfung kann lokale und technische Nachweise liefern. Domain,
Registry-Veröffentlichung, externe Freigaben und betriebliche Schwellenwerte werden
durch diesen Arbeitsstand nicht stellvertretend eingerichtet.
