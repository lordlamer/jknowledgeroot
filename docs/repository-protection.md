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

Zusätzlich gilt das unten beschriebene Ruleset für geprüfte Änderungen.

## Freigabemodell für einen alleinigen Entwickler

Seit dem 19. September 2026 ist für `master` der Check `verify` von GitHub Actions
(App-ID `15368`, im Repository geprüft) mit aktuellem Basisbranch erforderlich.
`dependency-graph` darf kein erforderlicher PR-Check werden: Dieser Job läuft
erst nach einem erfolgreichen Push auf `master`. `publish` läuft nur auf Tags.
Offene Review-Kommentare müssen vor dem Merge erledigt sein. Force-Pushes und
Löschen bleiben verboten.

| Einstellung | Einzelperson | Unabhängige Freigabe |
| --- | --- | --- |
| Änderungen an `master` | PR mit erfolgreichem `verify`, keine fremde Review-Freigabe erforderlich | PR mit erfolgreichem `verify` und mindestens einer fremden Freigabe |
| Neue Commits im PR | CI erneut prüfen | CI erneut prüfen, alte Freigaben verwerfen |
| Reviewer der Umgebung `release` | Repository-Eigentümer | Benannte weitere Person oder Team |
| Prevent self-review | Aus; bewusste eigene Release-Bestätigung bleibt erforderlich | An |

Der Eigentümer hat das Modell **Einzelperson** bestätigt. Ruleset **23698955**
setzt es ohne Bypass-Akteure um; die Konfiguration liegt unter
[master-verification.json](../.github/rulesets/master-verification.json).
Es verlangt weder Code-Owner-Freigaben noch die Zustimmung einer weiteren Person
zum letzten Push oder zu nicht zugeordneten Änderungen. Die zweite Spalte der
Tabelle beschreibt eine eigene Bestätigung, kein Vier-Augen-Prinzip.

Neue Änderungen gehen auf einen Arbeitsbranch und anschließend als Pull Request
nach `master`. Direkte Pushes nach `master` sind gesperrt. Nach erfolgreichem
`verify` und erledigten Review-Kommentaren kann der Eigentümer selbst mergen.
Ist der Basisbranch inzwischen weitergelaufen, muss der PR aktualisiert und
erneut geprüft werden. Auch reine Dokumentationsänderungen durchlaufen `verify`.

## Umgebung `release` einrichten und prüfen

Die Umgebung **release** ist eingerichtet: Reviewer **lordlamer** (Benutzer-ID
`1414066`), eigene Bestätigung erlaubt, Administrator-Bypass deaktiviert und
ausschließlich Tags mit Muster `v*` zugelassen. Die API-Konfigurationen stehen
unter [release.json](../.github/environments/release.json) und
[release-tag-policy.json](../.github/environments/release-tag-policy.json).
Die Benutzer-ID ist repositoryspezifisch und bei einem Fork anzupassen.

Unter **Settings → Environments → release** mindestens einen *Required reviewer*
eintragen und *Prevent self-review* entsprechend dem gewählten Modell setzen.
Administrator-Bypass für die Umgebung deaktivieren. Unter *Deployment branches
and tags* ausschließlich **Tag `v*`** erlauben; keine Branch-Regel und keine
zusätzlichen Muster. `DOCKERHUB_USERNAME` und `DOCKERHUB_TOKEN` ausschließlich
als Secrets dieser Umgebung hinterlegen. Geheimnisse gehören nicht in dieses
Dokument, in Shellargumente oder in Prüfberichte.

**Noch offen: Registry-Secrets umstellen.** Am 19. September sind
`DOCKERHUB_USERNAME` und `DOCKERHUB_TOKEN` als Repository-Secrets vorhanden,
die neue Umgebung enthält noch keine Secrets. GitHub gibt vorhandene Secret-Werte
nicht zurück; sie lassen sich deshalb nicht durch Auslesen übernehmen.
Der Eigentümer hinterlegt beide Werte direkt unter
[Settings → Environments → release](https://github.com/lordlamer/jknowledgeroot/settings/environments)
als Environment-Secrets. Anschließend die beiden gleichnamigen Repository-Secrets
unter **Settings → Secrets and variables → Actions** entfernen und die Ablage
erneut anhand der Namen prüfen. Die bestehenden Repository-Secrets wurden nicht
verändert. Zugangsdaten nicht in einen Chat oder in Git eingeben.

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

- Die aktiven Rulesets per API zurücklesen und mit den drei JSON-Dateien vergleichen.
- Erforderlichen CI-Check und das bestätigte Einzelpersonenmodell kontrollieren.
- Umgebung und Tag-Regel prüfen; der lesende Release-Check muss erfolgreich sein.
- Die fehlenden Regeln absichtlich nur in automatisierten Test-Fixtures simulieren;
  dafür keine echte Schutzregel deaktivieren und keinen Release-Tag erzeugen.
- Die eigentliche Veröffentlichung erst nach [Betriebsabnahme](operational-acceptance.md)
  gemäß [release.md](release.md) freigeben.

Am 19. September wurden die aktiven Regeln, der Reviewer, der ausgeschaltete
Administrator-Bypass und die alleinige Tag-Regel per API zurückgelesen.
Der echte lesende Release-Check besteht. Nachweise:
`target/r33-repository-after.json`, `target/r33-release-check.log` und
`target/r33-secret-locations.json` (nur Secret-Namen).
Es wurde kein Test-Tag und keine Veröffentlichung erzeugt. Ein tatsächlicher
PR-Merge mit Pflichtprüfung und eine Release-Bestätigung sind damit noch nicht
praktisch nachgewiesen; der vollständige Publish-Ablauf bleibt Teil der Abnahme.

GitHub kann eine im Workflow genannte, fehlende Umgebung automatisch ohne
Schutzregeln anlegen. Deshalb genügt `environment: release` allein nicht.
Siehe [GitHub: Umgebungen verwalten](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).
Die lesende Prüfung nutzt die dokumentierten APIs für
[Umgebungen](https://docs.github.com/en/rest/deployments/environments) und
[Branch-/Tag-Beschränkungen](https://docs.github.com/en/rest/deployments/branch-policies).
