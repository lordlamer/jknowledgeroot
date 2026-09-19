# Repository- und Release-Schutz

Der Schutz besteht aus getrennten Ebenen: unveränderliche Historie und Release-Tags,
geprüfte Änderungen am Hauptbranch und eine bewusste Freigabe der Veröffentlichung.
Der technische Release-Check ersetzt weder die GitHub-Freigabe noch die Betriebsabnahme.

## Schutz der bestehenden Historie

Die überprüfbaren API-Konfigurationen liegen unter
[master-integrity.json](../.github/rulesets/master-integrity.json) und
[release-tag-integrity.json](../.github/rulesets/release-tag-integrity.json).
Sie verhindern Force-Pushes und das Löschen von `master` sowie das Verschieben
und Löschen vorhandener `v*`-Tags. Es gibt keine Bypass-Akteure, auch keine
Administrator-Ausnahme. Administratoren können die Regeln selbst weiterhin ändern;
das ist keine Absicherung gegen einen böswilligen Repository-Eigentümer.
Andere Branches und Tags sind nicht betroffen. Neue Release-Tags bleiben möglich.

Diese Basisregeln entscheiden noch nicht über erforderliche Reviews oder
Merge-Verfahren. Änderungen solcher Regeln sind gesondert abzunehmen.

## Freigabemodell festlegen

Für `master` ist anschließend der Check `verify` von GitHub Actions
(App-ID `15368`, im Repository geprüft) mit aktuellem Basisbranch erforderlich.
`dependency-graph` darf kein erforderlicher PR-Check werden: Dieser Job läuft
erst nach einem erfolgreichen Push auf `master`. `publish` läuft nur auf Tags.
Offene Review-Kommentare sollen vor dem Merge erledigt sein. Force-Pushes und
Löschen bleiben verboten.

| Einstellung | Einzelperson | Unabhängige Freigabe |
| --- | --- | --- |
| Änderungen an `master` | PR mit erfolgreichem `verify`, keine fremde Review-Freigabe erforderlich | PR mit erfolgreichem `verify` und mindestens einer fremden Freigabe |
| Neue Commits im PR | CI erneut prüfen | CI erneut prüfen, alte Freigaben verwerfen |
| Reviewer der Umgebung `release` | Repository-Eigentümer | Benannte weitere Person oder Team |
| Prevent self-review | Aus; bewusste eigene Release-Bestätigung bleibt erforderlich | An |

Das gewählte Modell und gegebenenfalls der GitHub-Name der weiteren Person sind
noch zu bestätigen. Die zweite Spalte beschreibt eine eigene Bestätigung,
kein Vier-Augen-Prinzip.

## Umgebung `release` einrichten und prüfen

Unter **Settings → Environments → release** mindestens einen *Required reviewer*
eintragen und *Prevent self-review* entsprechend dem gewählten Modell setzen.
Administrator-Bypass für die Umgebung deaktivieren. Unter *Deployment branches
and tags* ausschließlich **Tag `v*`** erlauben; keine Branch-Regel und keine
zusätzlichen Muster. `DOCKERHUB_USERNAME` und `DOCKERHUB_TOKEN` ausschließlich
als Secrets dieser Umgebung hinterlegen. Geheimnisse gehören nicht in dieses
Dokument, in Shellargumente oder in Prüfberichte.

Der Workflow prüft bei Release-Tags vor dem Build und nochmals im Publish-Job
vor dem Registry-Login die tatsächlich gelesene GitHub-Konfiguration. Fehlt die
Umgebung, ein Reviewer oder die genaue Tag-Beschränkung, bricht er ab. Das gilt
auch bei nicht lesbarer API, fehlender Berechtigung oder unvollständiger Antwort.
Ein Timer allein genügt nicht. Beide Aufrufe verwenden nur `actions: read` und
`contents: read`; die Prüfung ändert keine Einstellungen.

Die erneute Prüfung erkennt beispielsweise eine zwischenzeitlich entfernte
Reviewer-Regel. Sie belegt, dass eine Freigaberegel konfiguriert ist; die
eigentliche Freigabe erzwingt GitHub. Administrator-Bypass, Secret-Ablage,
Reviewer-Berechtigungen und eine tatsächliche Release-Bestätigung sind zusätzlich
bei der Betriebsabnahme zu kontrollieren. Ein erfolgreicher Test mit simulierten
API-Antworten ist keine Freigabe des realen Zielsystems.

Lesender Aufruf mit einem bereits sicher bereitgestellten Token:

```sh
GITHUB_REPOSITORY=lordlamer/jknowledgeroot node scripts/release-protection.mjs
```

`GITHUB_TOKEN` muss im Prozess vorhanden sein. Nicht als Literal in den Aufruf
einsetzen. Unter Windows kann die projektlokale Node-Binärdatei
`target/frontend/node/node.exe` verwendet werden.

## Abnahme

- Die aktiven Rulesets per API zurücklesen und mit den beiden JSON-Dateien vergleichen.
- Nach Wahl des Freigabemodells erforderlichen CI-Check und Reviews konfigurieren.
- Umgebung und Tag-Regel prüfen; der lesende Release-Check muss erfolgreich sein.
- Die fehlenden Regeln absichtlich nur in automatisierten Test-Fixtures simulieren;
  dafür keine echte Schutzregel deaktivieren und keinen Release-Tag erzeugen.
- Die eigentliche Veröffentlichung erst nach [Betriebsabnahme](operational-acceptance.md)
  gemäß [release.md](release.md) freigeben.

GitHub kann eine im Workflow genannte, fehlende Umgebung automatisch ohne
Schutzregeln anlegen. Deshalb genügt `environment: release` allein nicht.
Siehe [GitHub: Umgebungen verwalten](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).
Die lesende Prüfung nutzt die dokumentierten APIs für
[Umgebungen](https://docs.github.com/en/rest/deployments/environments) und
[Branch-/Tag-Beschränkungen](https://docs.github.com/en/rest/deployments/branch-policies).
