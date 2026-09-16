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

**Passwort vergessen per E-Mail** ist vorerst zurückgestellt, bis der gewünschte
Umfang bestätigt ist. Administratoren können bereits im Benutzerformular ein neues
Passwort setzen. Ein Selbstbedienungs-Reset benötigt eine eindeutige verifizierte
Mailadresse, konfigurierbaren Versand, öffentliche Basis-URL, begrenzte Einmal-Tokens,
Missbrauchsschutz und neutrale Antworten. Siehe [OWASP zur Passwortwiederherstellung](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).

## Seiten verschieben

Administratoren wählen **More → Move page**. Die Zielauswahl lässt sich über Ebenen
durchsuchen oder nach einem Begriff filtern; pro Fenster werden höchstens 50 Ziele
angezeigt. **Move here** verschiebt die Seite samt Unterseiten. IDs, Links,
Kommentare, Labels und Anhänge bleiben bestehen.

Verschieben ist zunächst Administratoren vorbehalten, weil es vererbte Freigaben
verändern kann. Lokale Freigaben bleiben erhalten. Vererbende Seiten übernehmen
die Rechte der neuen Elternseite; Unterseiten behalten ihren Vererbungsmodus.
Beim Verschieben auf die oberste Ebene werden bisherige wirksame Freigaben als
lokale Rechte übernommen. Die Zielauswahl erläutert diese Auswirkungen.

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
