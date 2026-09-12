# Seitenrechte und dynamische Vererbung

Die folgenden Regeln wurden für R05 beschlossen. Öffentlich lesbar bedeutet
nicht automatisch öffentlich bearbeitbar.

## Berechtigungsmatrix

| Vorgang | Regel |
| --- | --- |
| Neue Hauptseite als angemeldeter Benutzer | Erlaubt; zunächst nur Ersteller mit Bearbeitungsrecht und Administratoren |
| Neue Hauptseite als Gast | Nur bei aktivierter Gast-Erstellung; öffentlich lesbar, ohne Gast-Bearbeitungsrecht |
| Neue Unterseite | Bearbeitungsrecht auf der Elternseite erforderlich, auch für Gäste; Administratoren dürfen immer anlegen |
| Standardrechte neuer Unterseiten | Dynamische Vererbung der Elternrechte, ohne zusätzliche Ersteller- oder Gastfreigabe |
| Vorhandene Seiten lesen und bearbeiten | Bestehende individuelle Benutzer-, Gruppen- und Gastrechte gelten weiter |
| Freigaben anzeigen und verwalten | Nur Administratoren |
| REST-Schreibzugriff | Bleibt zunächst auf Administratoren beschränkt; neue Seiten bekommen dieselben Standardrechte wie in der Oberfläche |

Explizite Gast-Bearbeitungsrechte erlauben auch das Anlegen einer Unterseite.
Diese Unterseite ist damit zunächst ebenfalls öffentlich les- und bearbeitbar.
Wird die Gastfreigabe auf der Elternseite später entzogen, gilt der Entzug auch
für alle darunter erbenden Seiten. Es wird kein zusätzlicher öffentlicher
Eintrag angelegt, der diesen Rechteentzug umgehen würde.

## Hauptseiten durch Gäste

`KR_ALLOW_GUEST_ROOT_CREATION` beziehungsweise
`knowledgeroot.pages.allow-guest-root-creation` steuert ausschließlich das Anlegen
von Hauptseiten durch Gäste. Standard ist `false`; `true` erlaubt die Erstellung
öffentlich lesbarer Hauptseiten. Die Einstellung gilt für Formular und POST;
die Navigation blendet die entsprechende Schaltfläche passend ein.

Unterseiten richten sich unabhängig davon nach den Elternrechten. Ein Gast mit
reinem Leserecht kann keine Unterseite erstellen. Der REST-Schreibzugriff bleibt
wie bisher Administratoren vorbehalten.

## Vererbung und ausdrückliche Ausnahmen

Für neue Unterseiten setzt die Anwendung `page.inherit_permissions = true`.
Die Rechteprüfung folgt der Elternkette bis zur nächsten Seite mit eigenen Rechten.
Änderungen an dieser Quelle wirken bei der nächsten Prüfung, ohne Rechte auf
Unterseiten kopieren oder Sitzungen neu aufbauen zu müssen. Der Ersteller erhält
auf erbenden Unterseiten keine zusätzlichen, dauerhaften Rechte.

Administratoren können im Berechtigungsbereich des Editors zwischen zwei Modi wechseln:

- **Eigene Rechte verwenden:** Die aktuell wirksamen Freigaben werden als lokale
  Einträge kopiert. Von diesem Zeitpunkt an wirken spätere Elternänderungen nicht
  mehr auf diese Seite und ihre erbenden Nachfahren. Die Oberfläche weist vor dem
  Wechsel darauf hin.
- **Rechte der Elternseite erben:** Lokale Freigaben werden entfernt. Es gelten
  sofort und künftig die dynamisch ermittelten Elternrechte. Hauptseiten können
  diesen Modus nicht verwenden.

Solange eine Seite erbt, sind lokale Freigabeänderungen gesperrt. Der Moduswechsel
ist ein ausdrücklicher, nur für Administratoren zugänglicher Vorgang. Zyklen,
fehlende oder gelöschte Eltern unterbrechen die Vererbung; normale Benutzer und
Gäste erhalten dadurch keinen Zugriff. Administratoren behalten Zugriff auf
vorhandene Seiten, um Probleme prüfen zu können.

Bestehende Rechte sind weiterhin additive Freigaben: Ein Eintrag `none` erzeugt
keine Freigabe und überschreibt keine anderweitige Gruppen- oder Gastfreigabe.
Dateien, Suche, Navigation, Kommentare und Sterne verwenden dieselbe gemeinsame
Seitenrechteprüfung.

## Bestehende Installationen

Changeset `1.0.7-page-permission-inheritance` fügt den Modus mit Standard `false`
hinzu. Vorhandene Seiten behalten dadurch ihre bisherigen Freigaben; sie werden
nicht automatisch auf dynamische Vererbung umgestellt. Administratoren können
dies nach Prüfung pro Unterseite ausdrücklich tun. Auch vorhandene öffentliche
Bearbeitungsrechte bleiben erhalten, erlauben aber keine Freigabeverwaltung mehr.

## Durchsetzung in Oberfläche und API

- `PageCreationService` erstellt Seiten mit den genannten Standardrechten und
  Labels in einer Transaktion. Auditdaten stammen aus dem aktuellen Benutzerkontext;
  mitgesendete Ersteller- und Seiten-IDs werden nicht übernommen. UI und REST nutzen
  denselben Dienst; REST liefert die tatsächlich erzeugte ID in `Location` zurück.
- `PageController` bietet eigene Endpunkte für Freigaben und akzeptiert zusätzlich
  Änderungen an Freigaben beim Speichern des normalen Seitenformulars. Eine
  Berechtigungsprüfung erfolgt vor dem Speichern von Inhalt oder Labels,
  damit ein unerlaubter Freigabeversuch keine teilweise gespeicherte Seite erzeugt.
- Der Seiteneditor lädt und zeigt die Berechtigungsliste nur Administratoren.
  Auch Benutzer-/Gruppenauswahl und direkte Freigabe-Endpunkte verlangen diese Rolle.
- Die Formulare für neue Haupt- und Unterseiten sowie ihre POST-Endpunkte verwenden
  dieselbe Prüfung für die Erstellung. Die Navigation bildet die Möglichkeiten des
  aktuellen Benutzers ab.

## Automatisierte Prüfung und Grenzen

`PagePermissionDatabaseTest` prüft mit einer isolierten MariaDB:

- Benutzerrechte gelten nur für den zugeordneten Benutzer und die betreffende Seite.
- Gruppenrechte setzen eine aktuelle Mitgliedschaft voraus; ihr Entzug wirkt bei
  der nächsten Berechtigungsprüfung.
- Explizite Gast-Leserechte erlauben Lesen, aber kein Bearbeiten.
- Administratoren können Seiten ohne individuellen Freigabeeintrag lesen und bearbeiten.
- Ändern und Löschen einer Berechtigung über eine fremde Seiten-ID wird abgelehnt;
  der bestehende Freigabeeintrag bleibt erhalten.

Zusätzlich geprüft sind mehrstufige Vererbung und Entzug, explizite Ausnahmen,
Rückkehr zur Vererbung, ungültige Hierarchien, private Hauptseiten, erlaubte und
gesperrte Gast-Erstellung sowie ein Rollback nach Fehlern beim Anlegen der Labels.

`PageAccessWebTest` verwendet die Security-Filterkette, echte Controller und den
Erstellungsservice mit gemockten DAOs. Er prüft Haupt-/Unterseiten, Freigabe-Endpunkte,
manipulierte Speicherformulare, UI-/API-Konsistenz und gerenderte Thymeleaf-Ansichten.
`InstallationTest` prüft den Erhalt bestehender Seiten, Freigaben und Changeset-Checksummen.

Ein interaktiver Browsertest der HTMX-Moduswechsel und ein vollständiger Betrieb
mit HTTP, Datenbank und Storage stehen weiterhin in R13 aus. Die Vererbung lädt
die Elternkette bei der Rechteprüfung; Abfrageoptimierung und Pagination folgen in
R10. Die übrigen Bearbeitungsabläufe werden unter R09 weiter transaktional abgesichert.
