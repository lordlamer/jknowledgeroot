# Client-API für Apps (`/api/v1`)

Die mobile Knowledgeroot-App (Flutter) nutzt eine JSON-Schnittstelle unter `/api/v1`.
Sie bildet die Funktionen der Web-UI ab und verwendet dieselben Domänendienste,
Berechtigungsprüfungen, Revisionskontrollen und Eingabegrenzen. Die bisherige REST-API
(`/page`, `/file`, `/user`, `/group`) bleibt unverändert.

## Anmeldung, Sitzung und CSRF

Die API verwendet die normale Serversitzung (Cookie `SESSION`) und den CSRF-Schutz
der Web-UI. Es gibt keine zusätzlichen Tokens und keine Datenbankänderung dafür.

1. `GET /api/v1/session` liefert Identität, Fähigkeiten (z. B. Upload-Grenzen,
   Root-Seiten anlegen, Passwort-Wiederherstellung) und ein CSRF-Token
   (`csrf.headerName`, `csrf.token`).
2. Alle ändernden Requests senden das Token im Header `X-XSRF-TOKEN`.
3. `POST /api/v1/auth/login` mit `{"username", "password"}` meldet an. Wie beim
   Formular-Login gelten die Loginquoten, die Sitzungs-ID wird erneuert und das
   CSRF-Token rotiert; die Antwort enthält das neue Token. Verlangt das Konto
   einen zweiten Faktor, antwortet der Login mit `authenticated: false` und
   `mfa: {pending: true, requirement: "verify"}`; bis zum Code liefern alle
   anderen API-Routen `401` mit `detail: "Second factor required."`.
4. `POST /api/v1/auth/mfa` mit `{"code"}` (Authenticator- oder
   Wiederherstellungscode) schließt die Anmeldung ab und liefert die volle
   Sitzung mit neuem CSRF-Token; ein falscher Code gibt `401`, nach fünf
   Fehlversuchen endet die Sitzung. Bei `requirement: "enrol"` (die Policy
   verlangt einen Token, der noch nicht eingerichtet ist) richtet die App den
   Token mit `POST /api/v1/auth/mfa/setup` und `POST /api/v1/auth/mfa/confirm`
   ein; die Bestätigung schließt die Anmeldung zugleich ab.
5. `POST /api/v1/auth/logout` beendet die Sitzung.
6. `POST /api/v1/auth/forgot-password` mit `{"email"}` fordert wie die Web-UI eine
   Passwort-Wiederherstellung an und antwortet immer mit `204`.

`GET /api/v1/session` nennt in `serverVersion` die Maven-Version aus den
Build-Informationen und in `mfa` den Stand des zweiten Faktors: `pending`,
`requirement` (`verify`/`enrol`/null), `enrolled` und die `policy`
(`off`, `optional`, `required-for-admins`, `required`).

Ohne Anmeldung antworten geschützte API-Routen mit `401` statt mit dem
Login-Redirect der Web-UI. Gäste dürfen wie in der Web-UI öffentliche Seiten lesen.
Passwortänderung, Deaktivierung und Rollenwechsel beenden auch App-Sitzungen beim
nächsten Request.

Fehler sind `application/problem+json`. Bei `4xx` enthält `detail` die fachliche
Begründung (z. B. `Page has changed`); Serverfehler bleiben ohne interne Details.

Zeitstempel werden als ISO-8601 mit Zeitzonen-Offset geliefert
(`2026-10-25T10:15:00+01:00`), damit Clients in anderen Zeitzonen korrekt anzeigen.

## Endpunkte

| Bereich | Endpunkte |
|---|---|
| Spaces | `GET /spaces` (betretbare Spaces mit `key`, `name`, `homePageId`, `archived`, `canCreateRootPage`, `canManage`, `current`), `GET /spaces/{id}` (Details und bis zu 100 Hauptseiten), `POST /spaces/{id}/open` (merkt den Space für die Sitzung wie der Umschalter der Web-UI) |
| Navigation | `GET /tree?parent=&after=&space=` (50 Einträge, `nextAfter`, `hasChildren`, `starred`; `parent=0` liefert die Hauptseiten von `space`, ohne Angabe die des zuletzt in der Sitzung gewählten beziehungsweise des Standard-Space), `GET /starred?space=` (ohne `space` alle Favoriten, mit `space` nur die des Space), `GET /search?q=&start=&space=` (Treffer nennen ihren `space`) |
| Seiten | `GET/PUT/DELETE /pages/{id}` (Details nennen `space` mit `id`, `key`, `name`), `POST /pages` (`space` für eine neue Hauptseite; Unterseiten nehmen den Space der Elternseite), `PUT/DELETE /pages/{id}/star` |
| Verschieben | `GET /pages/{id}/move-options?parent=&query=&start=&space=` (bei `parent=0` die Hauptseiten von `space`, Antwort nennt `space`), `POST /pages/{id}/move` mit `{"parent", "space", "revision"}` (`space` nur für die oberste Ebene eines anderen Space) |
| Kommentare | `GET/POST /pages/{id}/comments`, `DELETE /pages/{id}/comments/{commentId}` |
| Anhänge | `GET/POST(multipart "file") /pages/{id}/files`, `GET /files/{id}/content`, `DELETE /files/{id}` |
| Verlauf | `GET /pages/{id}/history`, `GET /pages/{id}/history/{historyId}`, `GET /pages/{id}/history/compare?from=&to=`, `POST /pages/{id}/history/{historyId}/restore` |
| Freigaben (Admin) | `GET/POST /pages/{id}/permissions`, `PUT/DELETE /pages/{id}/permissions/{permissionId}`, `PUT /pages/{id}/permissions/mode` |
| Profil | `GET/PUT /profile`, `POST /profile/password`, `GET/POST /profile/recovery` |
| Zweiter Faktor | `GET /profile/mfa` (Status, Policy, Pflicht), `POST /auth/mfa/setup` (Secret, otpauth-URI, QR-SVG), `POST /auth/mfa/confirm` (erster Code → Wiederherstellungscodes, einmalig), `DELETE /profile/mfa` (Passwort und Code; `409` bei verpflichtender Policy) |
| Verwaltung (Admin) | `GET /pages/deleted`, `GET /admin/principals`, `GET/POST /admin/users`, `GET/PUT/DELETE /admin/users/{id}`, `GET/POST /admin/groups`, `PUT/DELETE /admin/groups/{id}` |

Seitenänderungen, Löschen, Verschieben und Wiederherstellen verlangen die aktuelle
`revision`; veraltete Stände werden mit `409` abgelehnt. Inhalte werden serverseitig
wie in der Web-UI bereinigt.

## Begleitende Korrekturen

- Migration `1.0.14-utf8mb4` stellt die Basistabellen von `utf8` (3 Byte) auf
  `utf8mb4` mit der Kollation `utf8mb4_uca1400_ai_ci` um. Vorher führten Emoji –
  auf Handytastaturen alltäglich – in Seiten, Kommentaren, Labels oder Namen zu
  einem Serverfehler. Die Umstellung baut die betroffenen Tabellen einmalig neu
  auf; bei großen Beständen ein Wartungsfenster einplanen. Hinweise für
  Bestände mit `utf8_general_ci` stehen in [installation.md](installation.md#upgrade-einer-bestehenden-installation).
- `GroupImpl.isGroupExist` nutzte einen ungültigen JPQL-Alias; das Anlegen von
  Gruppen über die REST-API schlug dadurch fehl.
- Der HTML-Sanitizer behält jetzt `pre` (Codeblock) und `hr` (Trennlinie). Beide bietet der
  Editor an, sie gingen bisher beim Speichern verloren.
- `UserDao.findById`/`GroupDao.findById` liefern für unbekannte IDs `null` statt einer
  `NullPointerException`, wie es die vorhandenen Aufrufer erwarten.

Die Integrationstests in `ClientApiIntegrationTest` prüfen Anmeldung, CSRF,
Berechtigungen, Revisionen, Anhänge, Freigaben, Verwaltung und Emoji-Speicherung
gegen eine echte MariaDB.
