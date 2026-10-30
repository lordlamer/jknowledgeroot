# Repository- und Release-Schutz

Der Schutz besteht aus getrennten Ebenen: unveränderliche Historie und Release-Tags
sowie geprüfte Änderungen am Hauptbranch. Die Veröffentlichung eines Release-Tags
läuft seit R43 ohne manuelle Freigabe; der Push des Tags ist die Entscheidung.
Die Rulesets ersetzen nicht die Betriebsabnahme.

## Schutz der bestehenden Historie

Die überprüfbaren API-Konfigurationen liegen unter
[master-integrity.json](../.github/rulesets/master-integrity.json) und
[release-tag-integrity.json](../.github/rulesets/release-tag-integrity.json).
Sie verhindern Force-Pushes und das Löschen von `master` sowie das Verschieben
und Löschen vorhandener `v*`-Tags. Es gibt keine Bypass-Akteure, auch keine
Administrator-Ausnahme. Administratoren können die Regeln selbst weiterhin ändern;
das ist keine Absicherung gegen einen böswilligen Repository-Eigentümer.
Andere Branches und Tags sind nicht betroffen. Neue Release-Tags bleiben möglich.

Zusätzlich gilt das unten beschriebene Ruleset für geprüfte Änderungen.

## Freigabemodell für einen alleinigen Entwickler

Seit dem 19. September 2026 ist für `master` der Check `verify` von GitHub
Actions
(App-ID `15368`, im Repository geprüft) mit aktuellem Basisbranch erforderlich.
`dependency-graph` darf kein erforderlicher PR-Check werden: Dieser Job läuft
erst nach einem erfolgreichen Push auf `master`. `publish` läuft nur auf Tags.
Offene Review-Kommentare müssen vor dem Merge erledigt sein. Force-Pushes und
Löschen bleiben verboten.

| Einstellung | Einzelperson | Unabhängige Freigabe |
| --- | --- | --- |
| Änderungen an `master` | PR mit erfolgreichem `verify`, keine fremde Review-Freigabe erforderlich | PR mit erfolgreichem `verify` und mindestens einer fremden Freigabe |
| Neue Commits im PR | CI erneut prüfen | CI erneut prüfen, alte Freigaben verwerfen |
| Veröffentlichung eines `v*`-Tags | Automatisch nach erfolgreichem `verify`; der Tag-Push durch den Eigentümer ist die Freigabe | Zusätzliche Bestätigung durch eine weitere Person in einer GitHub-Umgebung |

Der Eigentümer hat das Modell **Einzelperson** bestätigt. Ruleset **23698955**
setzt es ohne Bypass-Akteure um; die Konfiguration liegt unter
[master-verification.json](../.github/rulesets/master-verification.json).
Es verlangt weder Code-Owner-Freigaben noch die Zustimmung einer weiteren Person
zum letzten Push oder zu nicht zugeordneten Änderungen. Die Spalte
**Einzelperson** beschreibt kein Vier-Augen-Prinzip: Wer `master` ändern und
Tags pushen darf, veröffentlicht damit auch.

Neue Änderungen gehen auf einen Arbeitsbranch und anschließend als Pull Request
nach `master`. Direkte Pushes nach `master` sind gesperrt. Nach erfolgreichem
`verify` und erledigten Review-Kommentaren kann der Eigentümer selbst mergen.
Ist der Basisbranch inzwischen weitergelaufen, muss der PR aktualisiert und
erneut geprüft werden. Auch reine Dokumentationsänderungen durchlaufen `verify`.

## Veröffentlichung von Release-Tags

Bis R42 lief der Tag-Publish-Job in der GitHub-Umgebung `release`, die eine
eigene Bestätigung durch `lordlamer` verlangte; ein lesendes Skript prüfte diese
Regel vor dem Build und vor dem Registry-Login. Seit R43 (25. Oktober 2026)
entfällt beides auf Entscheidung des Eigentümers: Der Job `publish` startet
direkt nach erfolgreichem `verify`, die Umgebung `release` ist gelöscht (sie
enthielt keine Secrets oder Variablen), und die früheren Konfigurationen unter
`.github/environments/` sowie `scripts/release-protection.mjs` sind entfernt.
Der Klick war beim alleinigen Entwickler ein zweiter Schritt desselben Kontos
nach dem bewussten Tag-Push; die Registry-Secrets lagen ohnehin auf
Repository-Ebene, sodass die Umgebung sie nicht zusätzlich schützte.

Was die Veröffentlichung weiterhin absichert:

- `publish` läuft nur für Pushes von Tags `v*`, deren Commit zu `origin/master`
  oder einem Wartungsbranch `release/*` gehört, und erst nach dem
  vollständigen Verify-Job mit Tests, Containerstart, Proxy-/Lasttest und
  OS-Audits. Pull Requests und Branches erhalten keine Registry-Zugangsdaten.

Seit dem 30. Oktober 2026 gibt es den Wartungsbranch `release/1.0` (abgezweigt
von `master` bei `34fc81a`) für die Stabilisierung von 1.0.0 und spätere
1.0.x-Korrekturen, während `master` die nächste Entwicklung aufnimmt. Pushes
und Pull Requests auf `release/*` laufen durch `verify`. Die Rulesets gelten
bisher nur für `master` und bestehende `v*`-Tags; für `release/*` sind weder
PR-Pflicht noch Schutz gegen Force-Push eingerichtet. Soll der Branch wie
`master` geschützt werden, sind die Rulesets `master-integrity` und
`master-verification` um `refs/heads/release/**` zu erweitern.
- Der Tag muss der Maven-Version entsprechen, die Arbeitskopie sauber und die
  Audits bestanden sein; sonst bricht die Zusammenstellung der Release-Assets
  im Verify-Job ab ([release.md](release.md#github-release-und-jar-download)).
- Die Rulesets verhindern weiterhin das Umschreiben von `master` und das
  Verschieben oder Löschen bestehender `v*`-Tags. Ein veröffentlichter Tag
  bleibt dauerhaft auf seinem Commit.
- Tags pushen kann nur, wer Schreibrechte auf das Repository hat; beim
  alleinigen Entwickler ist das der Eigentümer.

Folgen: Ein versehentlich gepushter Tag veröffentlicht nach dem Verify-Lauf
ohne Rückfrage Images und ein GitHub-Release. Der Tag selbst lässt sich danach
nicht löschen; Docker-Hub-Tags und das GitHub-Release müssten von Hand entfernt
und der Stand durch einen neuen Tag ersetzt werden. Ein kompromittiertes Konto
oder ein kompromittierter Rechner des Eigentümers kann ohne weiteren Schritt
veröffentlichen. Dieses Risiko trägt der Eigentümer bewusst.

Soll wieder eine Bestätigung verlangt werden: im Job `publish` die Zeile
`environment: release` eintragen und die Umgebung unter **Settings →
Environments** mit mindestens einem *Required reviewer* und ausschließlich der
Tag-Regel `v*` anlegen. GitHub legt eine im Workflow genannte, fehlende Umgebung
sonst automatisch ohne Schutzregeln an; `environment: release` allein genügt
deshalb nicht. Siehe
[GitHub: Umgebungen verwalten](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

`DOCKERHUB_USERNAME` und `DOCKERHUB_TOKEN` liegen als Repository-Secrets vor
und werden von `publish-master` (für `latest`) und `publish` (für Versionstags)
verwendet. Ein eigenes, auf das Repository `lordlamer/knowledgeroot` begrenztes
Docker-Hub-Token ist empfohlen. Geheimnisse gehören nicht in dieses Dokument,
in Shellargumente oder in Prüfberichte. Seit R42 besitzt der Job `publish`
zusätzlich `contents: write`, um das GitHub-Release mit den geprüften Assets
anzulegen; die Rulesets verhindern weiterhin Änderungen an `master` und an
bestehenden `v*`-Tags.

## Abnahme

- Die aktiven Rulesets per API zurücklesen und mit den drei JSON-Dateien
  vergleichen.
- Erforderlichen CI-Check und das bestätigte Einzelpersonenmodell kontrollieren.
- Prüfen, dass der Job `publish` keine Umgebung referenziert, nur für Tags `v*`
  läuft und `verify` voraussetzt.
- Die eigentliche Veröffentlichung erst nach [Betriebsabnahme](operational-acceptance.md)
  gemäß [release.md](release.md) durch den Tag auslösen.

Am 19. September wurden die aktiven Regeln per API zurückgelesen; Nachweis:
`target/r33-repository-after.json`. Der Pull-Request-Ablauf wurde anschließend mit
[PR #262](https://github.com/lordlamer/jknowledgeroot/pull/262) praktisch
geprüft: `verify` und Codacy erfolgreich für `c04934d`, keine Review-Freigabe,
regulärer Merge durch `lordlamer` als `6722957`. Die Regeln waren vor und nach
dem Merge aktiv; es wurden keine Ausnahmen eingerichtet. Nachweise:
`target/r34-pr-final-before-merge.json` und `target/r34-pr-after-merge.json`.
Der Publish-Ablauf ist mit den Tags `v1.0.0-rc.2` und `v1.0.0-rc.3` nachgewiesen
([release.md](release.md)); beide liefen noch mit Bestätigung der inzwischen
entfernten Umgebung.
