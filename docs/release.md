# Release-Kandidat und Freigabe

Seit dem 30. Oktober 2026 trägt `master` die Entwicklungsversion
**1.1.0-SNAPSHOT**; `latest` entsteht daraus. Die 1.0-Linie wird auf dem
Wartungsbranch `release/1.0` stabilisiert, dort bleibt die Version
`1.0.0-rc.3`, bis das endgültige `v1.0.0` getaggt wird. Tags mit
SNAPSHOT-Version lehnt die Artefaktprüfung ab.

Der Kandidat **1.0.0-rc.3** ist seit dem 25. Oktober 2026 als Tag `v1.0.0-rc.3`
auf Commit `46c5aa0` veröffentlicht: Der
[Tag-Lauf 40180456687](https://github.com/lordlamer/jknowledgeroot/actions/runs/40180456687)
bestand `verify`, und nach Bestätigung der Umgebung `release` liegen beide
Images auf Docker Hub (`lordlamer/knowledgeroot:v1.0.0-rc.3`, Digest
`sha256:f9f483afc6df705927385c7a72d2d04846f0da98841a4558883c503bbbc3adff`;
`lordlamer/knowledgeroot:database-v1.0.0-rc.3`, Digest
`sha256:04634a6d57d046a25d3c65b06a994756c14a7a38d8892570454be388a7b78dfc`).
Erstmals gibt es dazu das
[GitHub-Release v1.0.0-rc.3](https://github.com/lordlamer/jknowledgeroot/releases/tag/v1.0.0-rc.3)
mit `knowledgeroot-1.0.0-rc.3.jar` (SHA-256
`47f0cf16ebe75757c7ed79b63e408a7e8ee88e0bb97e43c9e1cc2e561d1ed2c7`), `SHA256SUMS`,
Manifest und Auditberichten (R42, siehe
[GitHub-Release und JAR-Download](#github-release-und-jar-download)). Der
parallele `master`-Lauf desselben Commits hat `latest` mit einem eigenen,
abweichenden Digest veröffentlicht; beide Läufe bauen getrennt, bitgleiche
Images werden nicht behauptet. Das ist noch keine Produktionsfreigabe.

Der Kandidat **1.0.0-rc.2** ist seit dem 19. Oktober 2026 als Tag
`v1.0.0-rc.2` auf Commit `4620661` veröffentlicht: Der
[Tag-Lauf 39252289002](https://github.com/lordlamer/jknowledgeroot/actions/runs/39252289002)
bestand `verify`, und nach Bestätigung der Umgebung `release` liegen beide
Images auf Docker Hub (`lordlamer/knowledgeroot:v1.0.0-rc.2`, Digest
`sha256:133cd6896b1e74487468b3b9215f6293c253e360bccf56bb5bbf1ba7de3e2d7f`;
`lordlamer/knowledgeroot:database-v1.0.0-rc.2`, Digest
`sha256:fc7a5d37c2f55b706fca664e454b9687b9defb7ebd7e27b54eda95cb4583f469`).
Das ist noch keine Produktionsfreigabe. Produktive Lastmessung und die Abnahme
der tatsächlichen Betriebsumgebung bleiben offen. Backup, Upgrade und Rückkehr
beschreibt [recovery.md](recovery.md).

## Artefakte zuordnen

Der Maven-Build erzeugt `knowledgeroot-<Version>.jar` (etwa `knowledgeroot-1.0.0-rc.3.jar`) und eingebettete Builddaten
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
Namenskonventionen; seit dem 19. Oktober 2026 sind sie für 1.0.0-rc.2 belegt.
Unabhängig davon veröffentlicht jeder grüne Lauf auf `master` automatisch
`latest` und `database-latest` (seit 23. Oktober 2026): den letzten gemergten
Stand, ohne Freigabe und ohne Betriebsabnahme. Für das Deployment beide
Registry-Digests festhalten und explizit als `KR_APP_IMAGE` und `KR_DB_IMAGE` setzen.

## GitHub-Release und JAR-Download

Die geprüfte JAR ist ab `v1.0.0-rc.3` ohne Docker erhältlich. Workflow-Artefakte
sind dafür ungeeignet: Sie verlangen eine GitHub-Anmeldung, haben keine stabile
Adresse und laufen nach drei Tagen ab. Stattdessen legt der Tag-Publish-Job nach
dem Registry-Push ein GitHub-Release zum Tag an:

1. Im Verify-Job stellt `scripts/release-assets.mjs assemble` aus dem endgültigen
   Manifest die Assets unter `target/release-assets/` zusammen: die JAR,
   `release-manifest.json`, `dependency-audit.json`, beide OS-Scan-Zusammenfassungen
   und `SHA256SUMS` im Format von `sha256sum`. Die JAR-Prüfsumme muss dem
   Manifest entsprechen; ein veränderter Arbeitsbaum, ungeprüfte Audits oder ein
   abweichender Tag brechen den Schritt ab. Bei Tags wird das Verzeichnis als
   Artefakt `release-assets` (drei Tage) gesichert.
2. Der Publish-Job lädt dieses Artefakt und erzeugt nach dem Push mit
   `gh release create --verify-tag` das Release. Die Notizen nennen JAR-Prüfsumme,
   beide Registry-Digests, Git-Revision und Laufzeiten; GitHub ergänzt die
   automatisch generierten Änderungshinweise. Tags mit Vorabversion wie
   `v1.0.0-rc.3` werden als Pre-Release markiert. Existiert das Release bereits,
   werden nur die Assets ersetzt (`--clobber`), damit ein wiederholter Lauf keine
   Dubletten anlegt. Dafür besitzt ausschließlich dieser Job `contents: write`.

Download und Prüfung, beispielsweise für `v1.0.0-rc.3`:

```sh
base=https://github.com/lordlamer/jknowledgeroot/releases/download/v1.0.0-rc.3
curl -fsSLO "$base/knowledgeroot-1.0.0-rc.3.jar"
curl -fsSLO "$base/SHA256SUMS"
sha256sum -c --ignore-missing SHA256SUMS
```

Die Prüfsumme belegt die Übereinstimmung mit dem in der CI geprüften Build;
eine Signatur der Assets gibt es nicht. Die JAR benötigt Java 25 und eine
erreichbare MariaDB mit der Konfiguration aus [installation.md](installation.md)
und [production.md](production.md); das Container-Image bleibt der empfohlene Weg.

## Freigabeschritte

1. Sauberen Release-Commit auf `master` oder dem Wartungsbranch `release/1.0`
   vorbereiten; Korrekturen für eine veröffentlichte 1.0.x-Linie werden auf
   `master` entwickelt und per Backport auf `release/1.0` übernommen, der Tag
   entsteht dort. Kandidatenversion in der POM
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
4. Repository-Schutz gemäß [repository-protection.md](repository-protection.md)
   prüfen: Rulesets gegen Umschreiben von `master` und bestehenden `v*`-Tags
   sowie PR-Pflicht mit erfolgreichem `verify` sind aktiv. Eine manuelle
   Release-Bestätigung gibt es seit R43 nicht mehr: Der Push des Tags ist die
   Freigabe, der Publish-Job startet direkt nach erfolgreichem `verify`.
   Deshalb den passenden Tag, beispielsweise `v1.0.0-rc.3`, erst nach
   abgeschlossener Abnahme erstellen. Ein abweichender Tag und eine veränderte
   Arbeitskopie werden bei der Artefaktprüfung abgelehnt; ein veröffentlichter
   Tag lässt sich nicht mehr verschieben oder löschen. Ein Kandidat ist kein
   endgültiges `v1.0.0`-Release.
5. Beide Images, Release-Manifest, Scanberichte und Betriebsnachweise dauerhaft archivieren.
   Die dreitägige CI-Artefaktaufbewahrung ist kein Release-Archiv; das GitHub-Release
   hält ab `v1.0.0-rc.3` JAR, Manifest und Auditberichte dauerhaft vor, die
   Images selbst liegen nur in der Registry. Den veröffentlichten
   Registry-Digest zusätzlich festhalten und im Deployment referenzieren.

Die automatisierte Prüfung kann lokale und technische Nachweise liefern. Domain,
Registry-Veröffentlichung, externe Freigaben und betriebliche Schwellenwerte werden
durch diesen Arbeitsstand nicht stellvertretend eingerichtet.
