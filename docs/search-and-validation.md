# Suche, Eingabegrenzen und Fehlerantworten

Stand: R10, 12. September 2026.

## Seitenlisten und Suche

`GET /page` wendet die Leserechte in der Datenbank vor `LIMIT` und `OFFSET` an.
Die Oberfläche `/search?q=...` verwendet dieselbe Abfrage. Eine rekursive CTE
ermittelt die jeweils zuständige Seite für geerbte Rechte. Lokale Benutzer-,
Gruppen- und Gastrechte sowie die Administratorausnahme entsprechen der
Einzelprüfung in `PagePermissionImpl`. Inaktive oder gelöschte Benutzer werden
nicht auf Gastrechte zurückgestuft. Nicht auflösbare Vererbung und Zyklen gewähren
normalen Benutzern und Gästen keine Rechte; Administratoren behalten ihre
bisherige Ausnahme. Gelöschte Seiten werden nicht geliefert.

Die ID einer nicht lesbaren Elternseite wird bereits in der Listenabfrage als
`null` ausgegeben. Pro Liste fällt eine SQL-Abfrage an, ohne zusätzliche
Berechtigungs- oder Anhangsabfragen für jeden Treffer. Die Einzelansicht behält
ihre gesonderte Rechteprüfung.

| Anfrage | Grenze |
| --- | --- |
| `/page`, `/user`, `/group`: `limit` | Standard 50, erlaubt 1–100 |
| `start` | Standard 0, erlaubt 0–100.000 |
| `/search`: Fenster | 20 sichtbare Treffer; ein zusätzlicher Treffer bestimmt, ob „Next“ angeboten wird |
| Suchbegriff beziehungsweise Seiten-Inhaltsfilter | höchstens 200 Zeichen |
| Seiten-Namensfilter | höchstens 255 Zeichen |

Seitenlisten sind stabil nach aufsteigender Seiten-ID sortiert. Die Suche findet
Teilzeichenfolgen in Titel oder HTML-Inhalt. `%`, `_` und `!` werden als normale
Zeichen behandelt, nicht als SQL-Wildcards. Ein leerer Suchbegriff liefert keine
Treffer und löst keine Vollabfrage aus. Der Fehler im Inhaltsfilter (`description`
statt `content`) ist behoben.

Suchergebnisse enthalten kurze, als Text ausgegebene Auszüge mit höchstens
300 Zeichen zuzüglich Auslassungszeichen. Bilder und anderes eingebettetes HTML
werden in den Auszügen nicht gerendert. „Previous“/„Next“ funktionieren sowohl
mit normalen Links als auch mit HTMX und behalten den Suchbegriff bei. Die
angezeigte Anzahl bezieht sich auf das aktuelle Fenster, nicht auf alle Treffer.

## Schreib- und Filtervalidierung

Neue und bearbeitete Seiten benötigen einen nicht leeren Namen mit höchstens
255 Zeichen. Fehlender Inhalt wird als leerer Text behandelt; der übergebene
Inhalt ist auf 65.535 UTF-8-Bytes entsprechend der vorhandenen TEXT-Spalte begrenzt.
Einzelne Labels dürfen nach Trimmen höchstens 64 Zeichen lang sein; maximal
32 Labels sind erlaubt. Zu lange Eingaben werden abgelehnt, statt beim Speichern
unbemerkt gekürzt zu werden. Die serverseitige HTML-Bereinigung bleibt aktiv;
die Datenbank prüft zusätzlich ihre Zeichenkodierung und Speichergrenzen.

Seiten-IDs müssen positiv sein; die Elternangabe `0` bezeichnet weiterhin eine
Wurzelseite. Seiten-, Benutzer- und Gruppen-Listen lehnen ungültige Datumswerte
und umgekehrte Zeitintervalle ab. Das Format bleibt `yyyy-MM-dd'T'HH:mm:ss`;
ungültige Kalendertage werden nicht normalisiert und Filter nicht stillschweigend
ignoriert. REST-Seitenupdates erhalten vorhandene Aktivierungs-/Löschwerte,
wenn diese Felder nicht mitgesendet werden.

## Fehlerantworten

Eine gemeinsame MVC-Fehlerbehandlung liefert für Ausnahmen begrenzte
Problem-Detail-Antworten: 400 für ungültige Eingaben, 403 bei fehlendem Zugriff,
404 für nicht gefundene Datensätze, 409 für Datenbankkonflikte und 500 für
unerwartete Fehler. SQL, Zugangsdaten, Stacktraces und interne Exception-Texte
werden nicht in diese Antworten übernommen. Technische Fehler werden auf dem
Server protokolliert. Die Auswahllisten für Benutzer/Gruppen geben ebenfalls keine
Exception-Texte mehr aus.

Objektrechte werden vor dem Laden geschützter Inhalte geprüft. Eine abgelehnte
Seiten-ID kann deshalb auch bei einer nicht existierenden Seite 403 ergeben;
die Antwort bestätigt deren Existenz nicht. Nicht zur angegebenen Seite gehörende
Rechte-IDs werden nach der Rechteprüfung als nicht gefunden behandelt. UI-Aktionen
mit fehlenden Seitenrechten liefern nun 403 statt einer Erfolgsumleitung.

Bei fehlgeschlagenen HTMX-Requests zeigt die Oberfläche eine verständliche
Fehlermeldung. Das Formular und nicht gespeicherte Eingaben bleiben bestehen,
sodass sie korrigiert und erneut gesendet werden können. Sicherheitsfilter wie
Login und CSRF behalten ihre bisherige Behandlung außerhalb der MVC-Advice.

## Migration, Nachweise und Grenzen

Der neue Changeset `1.0.9-read-query-indexes` ergänzt Indizes für Eltern-/Vererbungs-
und Gruppenmitgliedschaftsabfragen. Historische Migrationen werden nicht verändert.
MariaDBs konfigurierte Rekursionsgrenze gilt auch für diese Abfrage. Eine Überschreitung
führt zu einem Fehler und nicht zur Freigabe unvollständig geprüfter Ergebnisse.

Tests vergleichen die SQL-Auswahl mit der bisherigen Einzelprüfung für Gäste,
Benutzer, Gruppen, Administratoren, gesperrte/unbekannte Konten, lokale Rechte,
mehrstufige Vererbung und beschädigte Hierarchien. Sie prüfen Pagination nach
Rechten, verborgene Eltern-IDs, den Inhaltsfilter und genau eine Listenabfrage.
Eine Fixture mit 5.000 Seiten prüft ein Ergebnisfenster hinter vielen privaten
Treffern. Laufzeiten werden zur Einordnung protokolliert; dies ist kein Lasttest
und keine garantierte Produktionslatenz.

Die Teilzeichenfolgensuche kann weiterhin große Teile der Seitentabelle lesen.
Für deutlich größere Datenbestände sind ein eigener Suchindex und Lasttests zu
prüfen. Offset-Pagination bietet bei gleichzeitig veränderten Daten keinen festen
Snapshot über mehrere Requests. Seitenleisten sowie Datei-/Kommentarlisten sind
von dieser Suchpagination nicht erfasst. Vollständige Betriebserprobung und
Produktionsfreigabe bleiben in der Roadmap offen.
