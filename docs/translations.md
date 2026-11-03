# Sprachen der Oberfläche

Knowledgeroot zeigt Menüs, Formulare, Meldungen, Skripttexte und die
Wiederherstellungs-Mails in der Sprache des Benutzers. Seiteninhalte werden
nicht übersetzt. Mitgeliefert sind Englisch (Basis und Rückfall), Deutsch,
Französisch, Spanisch, Italienisch und Niederländisch.

## Welche Sprache gilt

1. Die im Profil gewählte Sprache, oder die von einem Administrator in der
   Benutzerverwaltung gesetzte. Beide schreiben dieselbe Spalte `user.language`.
2. Steht dort „Automatisch" (leerer Wert), die beste unterstützte Sprache aus
   dem `Accept-Language` des Browsers. Das gilt auch für Gäste und die
   Anmeldeseite.
3. Sonst Englisch. Die Sprache des Servers spielt keine Rolle.

Der Benutzer wird bei jedem Request neu geladen, eine geänderte Sprache gilt
also sofort, auch in anderen Sitzungen. Nach einem Wechsel im Profil lädt die
Seite vollständig neu, damit Navigation und Skripttexte mitwechseln.
Wiederherstellungs-Mails gehen in der gespeicherten Sprache des Empfängers
hinaus, bei „Automatisch" in der Browsersprache der auslösenden Anfrage.

Beim Upgrade setzt das Changeset `1.0.19-user-language` alle nicht
unterstützten Werte auf „Automatisch". Bisher bekam jedes Konto den nie
ausgewählten Platzhalter `en_US`; solche Benutzer sehen nach dem Upgrade ihre
Browsersprache.

Die Client-API (`/api/v1`) und der MCP-Server antworten weiterhin englisch:
Die mobile App übersetzt bekannte Meldungen selbst, Assistenten erwarten
stabile Texte.

## Aufbau

| Teil | Ort |
|---|---|
| Texte | `src/main/resources/i18n/messages.properties` (Englisch) und `messages_<code>.properties` |
| Sprachliste | `org.knowledgeroot.app.i18n.Languages` |
| Auswahl der Sprache | `UserLocaleResolver` |
| Templates | `th:text="#{schlüssel}"`, Parameter `#{schlüssel(${wert})}`, Attribute `th:title`, `th:placeholder`, `th:hx-confirm` … |
| Skripte | `krT('schlüssel', 'English fallback', …)`, Schlüssel in der Datei mit Präfix `js.`; das Wörterbuch steht als `window.krI18n` im Seitenkopf |
| Fehlermeldungen der Dienste | englische Begründung der `ResponseStatusException`, in HTML-Controllern über `UiMessages.reason(e)` als `reason.<slug>` übersetzt |
| Datumsformate | `format.date`, `format.datetime`, `format.datetime-seconds` je Sprache |

Alle Texte laufen durch `java.text.MessageFormat`. Ein Apostroph wird deshalb
immer verdoppelt (`l''utilisateur`), Parameter heißen `{0}`, `{1}`. Inhalte, die
unverändert bleiben müssen (Lizenztext, URLs, Code-Beispiele), tragen das
HTML-Attribut `translate="no"`.

## Prüfungen im Build

- `MessageBundleTest`: Jede Sprache hat genau die englischen Schlüssel mit
  denselben Parametern, alle Werte sind gültige `MessageFormat`-Muster ohne
  einzelne Apostrophe, alle in Templates und Skripten benutzten Schlüssel
  existieren, die englischen Rückfalltexte der Skripte stimmen mit der Datei
  überein, und jede feste Begründung im Java-Code hat einen `reason.*`-Eintrag.
- `TemplateTextTest`: Kein Template enthält sichtbaren Text, sichtbare
  Attribute oder englische Literale in Ausdrücken ohne Schlüssel.
- `LanguagesTest` und `I18nWebIntegrationTest`: Auswahlreihenfolge, Profil,
  Benutzerverwaltung, übersetzte Fehlermeldungen und die Migration.

## Eine Sprache ergänzen

1. `messages.properties` nach `messages_<code>.properties` kopieren und alle
   Werte übersetzen; Schlüssel, Parameter und `''` bleiben.
2. Den Code mit dem Namen in der eigenen Sprache in `Languages.ALL` eintragen.
   Eine Migration ist nicht nötig: Gespeichert werden nur Codes aus dieser
   Liste, und „Automatisch" bleibt leer.
3. `./mvnw verify` laufen lassen; `MessageBundleTest` nennt fehlende oder
   abweichende Einträge.

## Grenzen

- Die mitgelieferten Übersetzungen außer Englisch sind maschinell erstellt
  und sollten von Muttersprachlern durchgesehen werden.
- Die Client-API bietet die Sprache noch nicht an; die mobile App hat ihre
  eigene Sprachwahl (Deutsch, Englisch).
- Die alte Spalte `user.timezone` wird weiterhin nicht ausgewertet; Zeiten
  erscheinen in der Zeitzone des Servers.
- Fehlermeldungen mit variablem Inhalt, die der Java-Code zusammensetzt,
  bleiben englisch.
