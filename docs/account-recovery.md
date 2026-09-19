# Passwort per E-Mail zurücksetzen

Die Funktion ist standardmäßig ausgeschaltet. Sie benötigt einen SMTP-Zugang und
eine feste öffentliche HTTPS-Adresse. Für direkte Starts und das Produktions-Compose
gelten dieselben Umgebungsvariablen:

| Variable | Bedeutung |
| --- | --- |
| `KR_RECOVERY_ENABLED` | `true` aktiviert Formulare und Versand. |
| `KR_PUBLIC_URL` | Öffentliche HTTPS-Origin ohne Unterpfad, etwa `https://wissen.example.org`. |
| `KR_MAIL_FROM` | Einzelne gültige Absenderadresse. |
| `KR_SMTP_HOST` | SMTP-Hostname. |
| `KR_SMTP_PORT` | Standard `587`. |
| `KR_SMTP_USERNAME`, `KR_SMTP_PASSWORD` | Zugang des Versandkontos. |
| `KR_SMTP_AUTH` | Standard `true`; `false` nur für entsprechend abgesicherte Relays. |

Die Vorlage steht in [production.env.example](../deploy/production.env.example).
SMTP verlangt standardmäßig STARTTLS mit Prüfung des Servernamens;
Verbindungs-, Lese- und Schreibtimeout sind
je fünf Sekunden. Die entsprechenden `spring.mail.*`-Eigenschaften können bei
direkten Starts konfiguriert werden. Eine aktivierte Funktion ohne SMTP-Host,
gültigen Absender oder HTTPS-Origin verhindert den Start. SMTP-Erreichbarkeit
wird dabei nicht geprüft. Ein Mailausfall setzt den allgemeinen Healthstatus
nicht auf DOWN; Warnungen im Anwendungslog weisen auf Versandfehler hin.

## Nutzung

1. Im Profil eine eigene gültige Mailadresse speichern.
2. **Confirm email for password recovery** öffnen, aktuelles Passwort eingeben
   und den Bestätigungslink anfordern. Die Adresse muss unter aktiven Konten
   eindeutig sein. Adressen werden ohne Berücksichtigung der Großschreibung
   verglichen; internationale Mailadressen werden derzeit nicht unterstützt.
3. Link öffnen und **Confirm my email** betätigen. Das Öffnen allein bestätigt nichts.
4. Bei vergessenem Passwort auf der Anmeldeseite **Forgot password?** wählen.
   Die Antwort bleibt unabhängig von Existenz und Eignung des Kontos gleich.
5. Reset-Link öffnen, neues Passwort mit 16–128 Zeichen zweimal eingeben und
   bestätigen. Danach erneut anmelden. Frühere Sitzungen verlieren ihre Anmeldung
   spätestens beim nächsten Request. Eine zusätzliche Mail informiert über die Änderung.

Wer keinen Zugang mehr zur bestätigten Adresse hat, wendet sich an den
Administrator. Ein Administrator kann weiterhin ein neues Passwort setzen.
Änderungen der Adresse im Profil oder in der Benutzerverwaltung entfernen
Bestätigung und ausstehende Links; auch beim Zurückändern ist neu zu bestätigen.

## Schutz und Grenzen

- Bestätigungs- und Reset-Links gelten 30 Minuten und genau einmal. Eine erneute
  Ausstellung ersetzt den vorigen Link desselben Zwecks. Passwortänderung,
  Deaktivierung oder Löschung des Kontos machen alte Links unbrauchbar.
- Tokens enthalten 256 zufällige Bits. Die Datenbank einschließlich JDBC-Sitzung
  speichert nur deren SHA-256-Digest. Links werden beim ersten Öffnen aus der
  sichtbaren URL entfernt. Formulare verwenden CSRF-Schutz, `no-store` und
  `no-referrer`. GET-Aufrufe ändern weder Passwort noch Bestätigung.
- Links verwenden ausschließlich die konfigurierte Origin, keinen Request-Host.
  Proxy-Zugriffslogs dürfen die Query-Parameter dieser Endpunkte nicht aufzeichnen.
- Eigene persistente Konten-/Quelladressenquoten verwenden die konfigurierten
  Loginlimits, ohne normale Anmeldeversuche zu sperren.
- Eine begrenzte Warteschlange mit 100 Plätzen erledigt Kontensuche und Versand
  außerhalb der öffentlichen Anfrage. Sie ist nicht dauerhaft: Bei Neustart,
  Überlast oder Versandfehler kann eine erneute Anforderung nötig sein.
  Bei einem gemeldeten Versandfehler wird der zugehörige Token entfernt.
  Die neutrale Formularantwort ist keine Zustellbestätigung.
- Migration `1.0.13-account-recovery` ergänzt Bestätigungen und Tokens. Vorhandene
  Adressen bleiben unbestätigt. Die [Upgrade-/Rollbackregeln](recovery.md) gelten.

Die automatisierten Prüfungen verwenden einen isolierten SMTP-Empfänger ohne
Weiterleitung. Vor Aktivierung im Zielbetrieb sind tatsächliche Zustellung,
HTTPS-Linkadresse und Absenderkonfiguration mit einem eigenen Testkonto zu prüfen.
Grundlage: [OWASP zur Passwortwiederherstellung](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
