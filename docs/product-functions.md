# Funktionsumfang für 1.0

Knowledgeroot soll interne Teams und öffentliche Wissensbestände bedienen. Der
Einstieg ist eher klein; größere Bestände bleiben ein Ziel für Lastprüfung und
schrittweise Begrenzung weiterer Listen. Eine Kapazitätszusage folgt daraus nicht.

## Passwort im Profil ändern

**Profile → Change password** verlangt das aktuelle Passwort, ein neues Passwort
mit 16–128 Zeichen und dessen Wiederholung. Ein gültiges älteres Passwort darf
bei der Bestätigung weiterhin kürzer sein. Leerzeichen bleiben bedeutungstragend.

Die Änderung betrifft ausschließlich das angemeldete Konto und erfordert CSRF-Schutz
sowie erneute Passwortprüfung. Fehlversuche verwenden separate Konten-/Quelladressen-
Schlüssel mit den konfigurierten Loginquoten. Gleichzeitige Passwortänderungen werden
nicht überschrieben. Fehlerformulare enthalten keine zurückgeschriebenen Passwörter.
Nach Erfolg endet die aktuelle Sitzung; andere Sitzungen verlieren durch die
Credential-Prüfung beim nächsten Request ihre Anmeldung. Die erneute Anmeldung
erfordert das neue Passwort. Grundlage: [OWASP zu Passwortänderungen](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#change-password-feature).

Auf schmalen Bildschirmen öffnet der Navigationsknopf die Seitenleiste über dem
Inhalt. Escape schließt sie wieder; Formularfelder bleiben in der sichtbaren Breite.

**Passwort vergessen per E-Mail** ist optional verfügbar. Zuerst die Mailadresse
im Profil speichern und über **Confirm email for password recovery** unter Angabe
des aktuellen Passworts einen Bestätigungslink anfordern. Erst nach Bestätigung
kann **Forgot password?** auf der Anmeldeseite einen Reset-Link senden.
Bestehende Mailadressen werden nicht automatisch als bestätigt übernommen.
Administratoren können weiterhin im Benutzerformular ein neues Passwort setzen.
Konfiguration, Ablauf und Grenzen stehen in [account-recovery.md](account-recovery.md).

## Seiten verschieben

Angemeldete Bearbeiter wählen **More → Move page**. Die Zielauswahl lässt sich über
Ebenen durchsuchen oder nach einem Begriff filtern; pro Fenster werden höchstens 50 Ziele
angezeigt. **Move here** verschiebt die Seite samt Unterseiten. IDs, Links,
Kommentare, Labels und Anhänge bleiben bestehen.

Verschieben erfordert aktuelle Bearbeitungsrechte auf der Ausgangsseite und der
Zielseite, unter der sie eingeordnet wird. Direkte, geerbte und Gruppenrechte
zählen; bloßes Leserecht am Ziel reicht nicht. Gäste können nicht verschieben.
Die Auswahl zeigt nur lesbare Seiten. Lesbare Vorfahren können zum Erreichen
bearbeitbarer Unterseiten durchlaufen werden. Beim Absenden werden die Rechte
erneut geprüft. Administratoren behalten ihre bisherigen Möglichkeiten.

Verschieben kann vererbte Freigaben verändern. Lokale Freigaben bleiben erhalten.
Vererbende Seiten übernehmen die Rechte der neuen Elternseite; Unterseiten behalten
ihren Vererbungsmodus.
Beim Verschieben auf die oberste Ebene werden bisherige wirksame Freigaben als
lokale Rechte übernommen. Diese oberste Ebene steht angemeldeten Bearbeitern
wie beim Anlegen einer Hauptseite offen. Die Zielauswahl erläutert die Auswirkungen.

Selbstbezüge, Ziele im eigenen Unterbaum sowie gelöschte oder fehlende Zielvorfahren
werden abgelehnt. Der bisherige Seitenstand wird in der Historie gesichert.
Bereits geöffnete Bearbeitungsformulare der Seite und aller Unterseiten werden
durch neue Revisionen ungültig. Eine gemeinsame Datenbanksperre verhindert
Kreisbezüge durch parallele Verschiebungen. Normale Inhaltsänderungen verwenden
weiterhin ihre bisherigen Zeilensperren.

Migration `1.0.12-page-moves` ergänzt eine Sperrtabelle mit einer Zeile. Die
[Upgrade-/Rollbackregeln](recovery.md) gelten weiterhin. Ein Inhalts-Restore aus
der Historie verschiebt die Seite nicht zurück; der alte Elternverweis bleibt
im Snapshot erhalten.

## Versionen vergleichen

**History → Compare with current** vergleicht einen gespeicherten Stand mit der
aktuellen Seite. Zwei gespeicherte Versionen lassen sich über die Auswahlfelder
vergleichen. **Select for comparison** merkt einen Ausgangsstand beim Weiterblättern;
**Compare with selected** wählt den zweiten Stand. Eindeutige Snapshot-IDs erlauben
auch die Auswahl alter Datensätze mit doppelten Versionsnummern.

Beide bereinigten Fassungen erscheinen samt Namen und Labels nebeneinander, auf
schmalen Bildschirmen untereinander. Gelöschter und hinzugefügter Name/Inhaltstext
werden markiert. Aufklappbare HTML-Unterschiede zeigen auch Formatierungs- und
Linkänderungen. Historisches HTML wird bereinigt; Differenztexte werden ausschließlich
HTML-escaped ausgegeben. Bei großen Änderungen erscheinen vollständige alte/neue
Blöcke, um Speicher- und Rechenaufwand zu begrenzen. Bei alten Versionen ohne
erfasste Labels bleibt deren Zustand ausdrücklich unbekannt.

Aktueller Seitenstand und Labels werden innerhalb derselben Lesetransaktion geladen.
Es gelten heutige Bearbeitungsrechte; gelöschte Seiten bleiben Administratoren
vorbehalten. Vergleichen ändert weder Inhalte noch Freigaben.

## Große Seitenbäume bedienen

Die Seitenleiste zeigt je Ebene zunächst bis zu 50 lesbare Seiten.
**Load more pages** ergänzt den nächsten Abschnitt in derselben Ebene;
bereits aufgeklappte Zweige bleiben bestehen. Die Reihenfolge bleibt nach
Seiten-ID sortiert. Beim Weiterladen werden die aktuellen Freigaben erneut
berücksichtigt. Die Fortsetzung verwendet die letzte angezeigte ID, damit
zwischenzeitlich gelöschte frühere Einträge keine weiteren Seiten überspringen.

Auf- und Zuklappen verändert nur den jeweiligen Zweig. Nachgeladene Abschnitte
und aufgeklappte Zweige werden in der Browsersitzung für ein Neuladen gemerkt;
bereits entzogene Rechte werden dadurch nicht umgangen. Die aktuelle Seite
wird auch in nachgeladenen Abschnitten markiert. Sterne bleiben bedienbar.
**Filter loaded pages** filtert die bereits geladenen Einträge; die globale
Suche durchsucht weiterhin den gesamten lesbaren Bestand.

Ein Request lädt nur Navigationsdaten (ID, Name, Revision und lesbaren
Elternverweis), keine Seiteninhalte oder Dateilisten. Die Datenbank wendet
die Sichtbarkeit vor der Begrenzung an. Die Einschränkungen und Messmethode
für große Bestände stehen in [load-testing.md](load-testing.md).

## Zweiter Faktor (Authenticator-App)

Benutzer richten unter **Profil → Two-factor authentication** einen Token für
eine Authenticator-App ein: QR-Code scannen oder Schlüssel manuell eingeben,
ersten Code bestätigen, die zehn Wiederherstellungscodes sicher ablegen. Sie
werden nur einmal angezeigt; jeder gilt einmal. Das Entfernen verlangt
Passwort und einen aktuellen Code.

Administratoren legen unter **Admin → Settings** die Policy fest:

| Policy | Wirkung |
| --- | --- |
| Off | Niemand wird nach einem Code gefragt; eingerichtete Tokens bleiben erhalten. |
| Optional | Wer einen Token hat, gibt nach dem Passwort einen Code ein; alle anderen melden sich nur mit Passwort an. |
| Required for administrators | Administratoren müssen beim nächsten Login einen Token einrichten; übrige Benutzer wie bei Optional. |
| Required for everyone | Alle müssen beim nächsten Login einen Token einrichten. |

Solange die Policy einen Token für das Konto verlangt, kann der Benutzer ihn
nicht selbst entfernen. Bei verlorenem Telefon setzt ein Administrator den
Token in der Benutzerverwaltung zurück; der Benutzer richtet danach einen
neuen ein. Fünf falsche Codes beenden die Sitzung, die Anmeldung beginnt von
vorn. Technische Details in [authentication.md](authentication.md#zweiter-faktor-totp).

## KI-Assistenten über MCP

Administratoren schalten unter Admin → Settings den MCP-Server ein. Danach
verbinden Benutzer Assistenten wie Claude mit `https://<host>/mcp`: Der
Assistent registriert sich selbst, der Benutzer meldet sich wie gewohnt an
(auch mit zweitem Faktor) und entscheidet auf der Zustimmungsseite, ob der
Assistent nur lesen oder auch Seiten anlegen, bearbeiten und kommentieren
darf. Der Assistent sieht genau die Spaces und Seiten, die der Benutzer sehen
darf, und schreibt in dessen Namen mit Revisionsprüfung; dazu gehören Baum,
Suche mit Filtern, Labels, Favoriten, Kommentare, Anhänge lesen, Verlauf mit
Vergleich, Verschieben, Löschen und Wiederherstellen. Administratoren sehen
unter Admin → MCP jeden Aufruf mit Benutzer, Assistent, Parametern und
Ergebnis und können Verbindungen trennen. Im Profil stehen die
verbundenen Assistenten mit Zugriff und Gültigkeit; dort lassen sie sich
trennen. Ein Passwortwechsel trennt alle. Administratoren pflegen die Liste
der Hosts, zu denen Assistenten nach der Anmeldung zurückkehren dürfen;
Assistenten auf dem eigenen Rechner sind immer erlaubt. Einzelheiten in
[mcp.md](mcp.md).

## Sprache der Oberfläche

Im Profil wählt jeder Benutzer unter **Language** eine Sprache oder
„Automatisch"; Administratoren setzen sie beim Anlegen oder Bearbeiten eines
Benutzers. „Automatisch" folgt der Browsersprache, ohne passende Sprache gilt
Englisch. Angeboten werden Englisch, Deutsch, Französisch, Spanisch,
Italienisch und Niederländisch. Die Sprache gilt für Menüs, Formulare,
Meldungen, Datumsformate und die Wiederherstellungs-Mails, nicht für
Seiteninhalte. Nach dem Speichern lädt die Seite in der neuen Sprache. Gäste
und die Anmeldeseite folgen der Browsersprache. Einzelheiten und wie man eine
Sprache ergänzt in [translations.md](translations.md).

## Spaces

Ein Space ist ein Container um einen eigenen Seitenbaum, vergleichbar mit
Confluence. Unter **Spaces** im Menü sehen Benutzer alle Spaces, die sie
betreten dürfen; Gäste sehen öffentliche Spaces. Jeder Space hat einen festen
Schlüssel (2–16 Großbuchstaben oder Ziffern), einen Namen, eine Beschreibung
und eine Homepage, die beim Wechsel in den Space geöffnet wird.

Die Seitenleiste zeigt immer genau einen Space: Der Umschalter in der
Kopfzeile neben dem Logo zeigt Schlüssel und Namen des aktuellen Space und
öffnet ein Menü mit allen betretbaren Spaces (Schlüssel, Name, Beschreibung,
Häkchen am aktuellen, Kennzeichen „archived") sowie den Einträgen „All
spaces" und „New space". Der Wechsel öffnet die Homepage des Space, ohne
Homepage die Space-Übersicht mit den Hauptseiten. Der Space-Name über dem
Baum führt zur Übersicht. Das Öffnen einer Seite, etwa aus der Suche oder
über einen Link, wechselt Seitenleiste und Umschalter in den Space der
Seite; der zuletzt gewählte Space bleibt in der Sitzung.
Auch die Favoriten in der Seitenleiste folgen dem gezeigten Space: Sterne an
Seiten anderer Spaces bleiben erhalten und erscheinen nach dem Wechsel
dorthin. Der Breadcrumb einer Seite beginnt mit ihrem Space. „Neue Seite" im
Baum legt eine Hauptseite im gezeigten Space an. Die Suche durchsucht alle
Spaces, die man betreten darf, und lässt sich auf einen Space einschränken;
die Treffer nennen den Space-Schlüssel.

- **Rechte am Space:** Lesen (betreten und lesen, was die Seitenrechte
  erlauben), Bearbeiten (zusätzlich Hauptseiten anlegen), Verwalten
  (zusätzlich Einstellungen, Freigaben, Archivierung). Freigaben gibt es für
  Benutzer, Gruppen, Gäste und alle angemeldeten Benutzer. Ohne Recht am Space
  ist keine seiner Seiten erreichbar; die Seitenrechte gelten innerhalb wie
  gewohnt.
- **Hauptseiten** übernehmen die Rechte ihres Space, solange ein Administrator
  sie nicht auf eigene Freigaben umstellt.
- **Anlegen:** Administratoren immer; andere Benutzer, wenn sie unter
  Admin → Permissions das Recht „Create spaces" erhalten haben. Wer einen Space
  anlegt, verwaltet ihn und bekommt automatisch eine Homepage.
- **Einstellungen:** Space-Verwalter ändern unter Spaces → Settings Name,
  Beschreibung, Homepage und Freigaben und archivieren den Space. Archivierte
  Spaces sind schreibgeschützt und nur für Verwalter und Administratoren
  sichtbar; der letzte aktive Space kann nicht archiviert werden.
- **Verschieben** einer Seite unter eine Seite eines anderen Space nimmt ihren
  Teilbaum mit in diesen Space. Im Verschieben-Dialog lässt sich auch die
  oberste Ebene eines anderen Space als Ziel wählen; dafür braucht man dort
  das Recht, Hauptseiten anzulegen.

Die Migration legt den Space „Knowledgeroot" (`KR`) an, der alle bisherigen
Seiten enthält; Gäste dürfen ihn lesen, angemeldete Benutzer dort Hauptseiten
anlegen. Die Client-API bietet Spaces unter `/api/v1/spaces` und nimmt
`space` bei Baum, Suche, Seitenanlage und Verschieben an
([client-api.md](client-api.md)). Regeln im Detail in
[access-control.md](access-control.md#spaces).

## Arbeitskopie, Entwürfe und automatisches Speichern

Seit R62 hat jede Seite neben der veröffentlichten Version eine
**Arbeitskopie**. Der Editor speichert zwei Sekunden nach der letzten
Eingabe automatisch in diese Arbeitskopie; die Statuszeile über dem Formular
zeigt „Saved at …" oder eine Fehlermeldung, bei der die Eingaben im Editor
bleiben. Leser sehen weiterhin die veröffentlichte Version. **Publish
changes** übernimmt das Formular als neue Version in die Historie und
entfernt die Arbeitskopie; die Historie wächst nur je Veröffentlichung.

- **Eine Arbeitskopie je Seite:** Alle Benutzer mit Schreibrecht sehen sie.
  Wer die Seite bearbeitet, startet mit der Arbeitskopie; die Seitenansicht
  zeigt Bearbeitern „Unpublished changes saved … by …" mit den Knöpfen
  **Publish**, **Continue editing** und **Discard working copy**.
  Verwerfen setzt den Editor auf die veröffentlichte Seite zurück.
- **Veraltete Arbeitskopie:** Jede Arbeitskopie merkt sich die Revision, bei
  der sie begann. Hat seither jemand anderes veröffentlicht, warnt Editor und
  Seitenansicht; Veröffentlichen aus dem alten Formular wird wie bisher mit
  409 abgelehnt, die Arbeitskopie bleibt zum Vergleichen erhalten.
- **Entwürfe:** **Save as draft** beim Anlegen erzeugt eine Seite ohne
  veröffentlichte Version. Sie erscheint nur für Benutzer mit Schreibrecht in
  Seitenleiste, Suche, Client-API und MCP, dort mit der Kennzeichnung
  „Draft"; Leser, Gäste und Links von außen erhalten 403. **Publish**
  veröffentlicht den Entwurf beziehungsweise seine Arbeitskopie; eine
  veröffentlichte Seite wird nicht wieder zum Entwurf.
- **Client-API und MCP:** `PUT /api/v1/pages/{id}` veröffentlicht wie bisher
  und verwirft die Arbeitskopie; `GET/PUT/DELETE /api/v1/pages/{id}/draft`
  und `POST /api/v1/pages/{id}/publish` bedienen die Arbeitskopie,
  `published: false` beim Anlegen erzeugt einen Entwurf. Das MCP-Tool
  `update_page` schreibt in die Arbeitskopie und veröffentlicht nur mit
  `publish=true`; `publish_page` veröffentlicht, `create_page` kennt
  `draft=true`.

Grenzen: Die Arbeitskopie fasst wie der Seiteninhalt bis zu 64 KB. Beim
Veröffentlichen aus dem Formular kann ein genau gleichzeitig laufendes
Autosave die eben veröffentlichte Arbeitskopie erneut anlegen; sie erscheint
dann als veraltete Arbeitskopie und lässt sich verwerfen. Gleichzeitiges
Bearbeiten derselben Arbeitskopie führt bis R63 zur Regel „der letzte
Autosave gewinnt". Migration `1.0.20-working-copy` ergänzt
`page.published` und die Tabelle `page_draft`.

## Gemeinsames Bearbeiten in Echtzeit

Seit R63 bearbeiten mehrere Benutzer dieselbe Seite gleichzeitig und sehen
Cursor, Markierung und Namen der anderen. Alle arbeiten auf derselben
Arbeitskopie: Was einer tippt, erscheint bei den anderen, ohne dass jemand
speichern muss; „Publish changes" veröffentlicht den gemeinsamen Stand.
Die Statuszeile unter dem Hinweis zum automatischen Speichern zeigt „Live
editing on" beziehungsweise „Live editing with n others".

- **Ablauf:** Beim Öffnen des Editors verbindet sich der Browser per
  WebSocket mit `/ui/page/{id}/collab` und erhält die bisherigen
  Änderungen der Arbeitskopie; der erste Browser auf einer leeren
  Arbeitskopie übernimmt den Inhalt des Formulars. Der Editor erscheint
  erst nach dem Abgleich, damit niemand auf einer leeren Seite tippt.
  Die Zusammenführung läuft im Browser (Yjs); der Server reicht die
  Änderungen durch und speichert sie, ohne sie zu interpretieren.
  Rückgängig und Wiederholen betreffen nur die eigenen Eingaben.
- **Zugang:** Der Socket verlangt dieselbe Sitzung wie der Editor, das
  Bearbeitungsrecht an der Seite und die eigene Herkunft der Anwendung;
  Gäste mit Schreibrecht dürfen wie im Editor. Verliert jemand das Recht,
  wird die Verbindung innerhalb einer halben Minute getrennt.
- **Veröffentlichen und Verwerfen** beenden die Live-Sitzung aller
  Beteiligten; deren Statuszeile bittet darum, den Editor neu zu laden.
  Veröffentlichen aus einem Formular mit veralteter Revision wird weiterhin
  mit 409 abgelehnt.
- **Ohne WebSocket** (etwa ein Proxy ohne Upgrade) fällt der Editor nach
  wenigen Sekunden auf das Verhalten von R62 zurück: automatisches Speichern
  der Arbeitskopie, aber keine Live-Anzeige; die Statuszeile sagt es.

Grenzen: Das gemeinsame Bearbeiten gilt für den Browser; die mobile App
schreibt weiter über die Arbeitskopie der Client-API. Die Live-Sitzungen
einer Seite liegen im Speicher einer Anwendungsinstanz; bei mehreren
Instanzen hinter einem Lastverteiler müssen die Bearbeiter derselben Seite
auf derselben Instanz landen (Sitzungsbindung), sonst sehen sie einander
nicht. Ein Browser, der die Verbindung verliert, verbindet sich bis zu fünf
Mal neu. Die Änderungen werden in `page_collab_update` gespeichert und
regelmäßig durch einen Browser verdichtet; Migration
`1.0.21-live-collaboration`. Proxy-Einstellungen in
[production.md](production.md#live-bearbeiten-hinter-dem-proxy).
