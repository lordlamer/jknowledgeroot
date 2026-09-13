# Bearbeitungskonflikte, Historie und gelöschte Seiten

Stand: R15, 13. September 2026, Kandidat **1.0.0-rc.2**.

## Gleichzeitiges Bearbeiten

Jede Seite besitzt eine fortlaufende `revision`. Das Bearbeitungsformular überträgt
die beim Öffnen geladene Revision. Speichern, Wiederherstellen und Löschen prüfen
diesen Stand erneut innerhalb der Datenbanktransaktion. Hat jemand inzwischen
gespeichert, antwortet die Anwendung mit **409 Conflict**. Name, Inhalt, Labels
und Historie des erfolgreichen Schreibers bleiben erhalten. Auch separat geänderte
lokale Freigaben oder ein Wechsel des Vererbungsmodus machen ein bereits geöffnetes
Bearbeitungsformular ungültig.

Bei HTMX bleibt der eigene Entwurf samt Editor im Browser stehen. Die Meldung
fordert dazu auf, die aktuelle Seite in einem zweiten Tab zu vergleichen. Es gibt
kein automatisches Zusammenführen und kein erzwungenes Überschreiben: Entwurf
zunächst kopieren, die aktuelle Seite neu laden und die gewünschten Änderungen
übertragen. Wiederholtes Speichern des alten Formulars umgeht den Konflikt nicht.
Ungespeicherte Entwürfe sind nicht serverseitig gesichert und gehen beim Schließen
oder Neuladen des Tabs verloren.

Eine fehlende oder negative Revision ergibt **428 Precondition Required**. Vor
dem Upgrade geöffnete Formulare müssen deshalb neu geladen werden. Bei einem
Update per REST enthält der GET-Datensatz die erforderliche Revision:

```json
{"id": 42, "revision": 3, "name": "Titel", "content": "<p>Inhalt</p>"}
```

`PUT /page/42` verlangt diesen Wert und liefert nach Erfolg die neue Revision.
`DELETE /page/42?revision=3` und die entsprechende UI-Route verlangen ihn ebenfalls.
Erstellung vergibt den Anfangswert selbst; vorgetäuschte Client-Versionen werden
dabei ignoriert. Clients müssen ihre bisherige Update-/Delete-Verwendung anpassen.

Die Prüfung umfasst Seiteninhalt, Labels und gemeinsam gespeicherte Freigaben.
Kommentare, Sterne und Anhänge bleiben eigenständige Vorgänge. Freigaben der
Elternseite und Gruppenmitgliedschaften werden über die bestehenden aktuellen
Berechtigungsprüfungen wirksam; sie sind keine wiederherstellbaren Inhaltsversionen.

## Frühere Versionen ansehen und wiederherstellen

Der Link **History** bei einer bearbeitbaren Seite führt zur Historie. Sie ist
bewusst nur mit aktuellen Bearbeitungsrechten zugänglich: Reine Leserechte auf den
heutigen Inhalt eröffnen keinen Zugriff auf möglicherweise sensible Altinhalte.
Beim Wiederherstellen werden die Rechte und die aktuelle Seitenrevision erneut
geprüft. Eine fremde Historien-ID lässt sich nicht einer anderen Seite zuordnen.

Vor jeder erfolgreichen Inhaltsänderung oder Löschung wird der bisherige Zustand
in derselben Transaktion archiviert. Schlägt ein Teil des Speicherns fehl, werden
auch Revision und Archivierung zurückgerollt. Die Historie zeigt jeweils 50 Einträge
und enthält Verweise auf einzelne Versionen. Die aktuelle Fassung steht auf der
Seite selbst; neue Seiten haben bis zur ersten Änderung noch keinen älteren Stand.
Separate Freigabeänderungen können Lücken in den angezeigten Versionsnummern
erzeugen, da sie den Bearbeitungsstand invalidieren, aber keine Inhaltsversion sind.

**Restore this version** übernimmt Namen, Inhalt und die damals erfassten Labels.
Die verdrängte aktuelle Fassung wird wiederum archiviert. Die ursprüngliche
Erstellungsidentität bleibt erhalten; als Ändernder gilt der tatsächlich angemeldete
Benutzer beziehungsweise der Gast. Historisches HTML wird beim Anzeigen und
Wiederherstellen mit der heutigen HTML-Policy bereinigt.

Freigaben, Vererbungsmodus, Seitenposition, Zeitsteuerung, Aktivierungsstatus,
Kommentare, Sterne und Anhänge werden nicht zurückgesetzt. Die Historie ist keine
vollständige Datensicherung und kein unveränderliches externes Auditprotokoll.

Vorhandene alte `page_history`-Zeilen und deren Nummern bleiben bei der Migration
erhalten. Der neue Zähler beginnt oberhalb der vorhandenen Historiennummern.
Labels, die früher nicht aufgezeichnet wurden, werden als unbekannt gekennzeichnet;
beim Restore einer solchen Version bleiben die aktuellen Labels erhalten. Erfasste
Labels müssen den heutigen Eingabegrenzen entsprechen; unzulässige alte Werte
führen zu einer Ablehnung und werden nicht still gekürzt. Inhalte lassen sich in
diesem Fall aus der Versionsansicht in ein aktuelles Bearbeitungsformular übernehmen.

## Löschen und Wiederherstellen gelöschter Seiten

Löschen markiert eine Seite als gelöscht. **Seiten mit nicht gelöschten Unterseiten
können nicht gelöscht werden**; der Versuch ergibt 409. Die Anwendung prüft dies
unter einer Datenbanksperre. Auch paralleles Anlegen oder Wiederherstellen einer
Unterseite darf keinen lebenden Kindknoten unter einer gelöschten Seite erzeugen.
Seiten werden nicht automatisch verschoben oder mit allen Nachfahren gelöscht.

Dateimetadaten und gespeicherte Objekte bleiben vorhanden. Downloads gelöschter
Seiten werden durch die Seitenberechtigungen gesperrt. Nach Wiederherstellung der
Seite sind deren Anhänge wieder erreichbar. Physische Bereinigung bleibt eine
separate Wartungsaufgabe gemäß [storage.md](storage.md); auch gelöschte Seiten und
Anhänge müssen bei der Ermittlung vorhandener Referenzen berücksichtigt werden.

Administratoren finden **Deleted pages** im Admin-Menü. Nur sie können gelöschte
Seiten samt Historie sehen und wiederherstellen. Eltern müssen vor ihren Kindern
wiederhergestellt werden. Für schon vor R15 gelöschte Seiten erzeugt die Migration
einen wiederherstellbaren Ausgangsstand. Eine Löschung per `deleted`-Flag im normalen
REST-Update ist gesperrt; der bisherige pauschale `DELETE /page` antwortet mit 405.

## Betrieb und Grenzen

Die Migration `1.0.11-page-revisions` ergänzt die Seitenrevision und Label-Snapshots.
Vor dem Upgrade [gemeinsam sichern](recovery.md), sämtliche alten App-Instanzen
stoppen und offene Editortabs danach neu laden. Alte Versionen kennen diese
Versionsprüfung nicht und dürfen nicht weiter auf dieselbe Datenbank schreiben.
Rollback erfolgt mit altem Image **und** Vor-Upgrade-Backup in einer leeren Umgebung.

Die neuen Datenbanktests prüfen gleichzeitiges Speichern, Lösch-/Erstellungsrennen,
Rollback, aktuelle Rechte, Restore und Altbestände. Der Chromium-Test verwendet
zwei Editortabs und prüft den erhaltenen Entwurf sowie die Historienoberfläche und
Wiederherstellung. Die vorhandene Restorekette prüft zusätzlich den Upgradepfad vom
festen R13-Baseline-Image auf den Kandidaten mit der neuen Migration.

Versionshistorie und gelöschte Dateien werden nicht automatisch ausgedünnt.
Datenbank-/Volume-Wachstum überwachen und vor einer späteren Lösch-/Aufbewahrungsregel
fachliche Anforderungen festlegen. Eine visuelle Änderungsgegenüberstellung,
automatisches Zusammenführen, Verschieben von Seiten und Aufbewahrungsautomatik
sind nicht implementiert. Die technische Umsetzung ersetzt keine Lastmessung,
externe Alarmierung oder Freigabe der tatsächlichen Produktionsumgebung.

Referenz für die zusätzlich berücksichtigte Datenbank-Konfliktmeldung:
[MariaDB: ER_CHECKREAD / Fehler 1020](https://mariadb.com/docs/server/reference/error-codes/mariadb-error-codes-1000-to-1099/e1020).
