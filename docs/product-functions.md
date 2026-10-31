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
