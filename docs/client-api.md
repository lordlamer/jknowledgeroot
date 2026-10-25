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
   CSRF-Token rotiert; die Antwort enthält das neue Token.
4. `POST /api/v1/auth/logout` beendet die Sitzung.

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
| Navigation | `GET /tree?parent=&after=` (50 Einträge, `nextAfter`, `hasChildren`, `starred`), `GET /starred`, `GET /search?q=&start=` |
| Seiten | `GET/PUT/DELETE /pages/{id}`, `POST /pages`, `PUT/DELETE /pages/{id}/star` |
| Verschieben | `GET /pages/{id}/move-options?parent=&query=&start=`, `POST /pages/{id}/move` |
| Kommentare | `GET/POST /pages/{id}/comments`, `DELETE /pages/{id}/comments/{commentId}` |
| Anhänge | `GET/POST(multipart "file") /pages/{id}/files`, `GET /files/{id}/content`, `DELETE /files/{id}` |
| Verlauf | `GET /pages/{id}/history`, `GET /pages/{id}/history/{historyId}`, `GET /pages/{id}/history/compare?from=&to=`, `POST /pages/{id}/history/{historyId}/restore` |
| Freigaben (Admin) | `GET/POST /pages/{id}/permissions`, `PUT/DELETE /pages/{id}/permissions/{permissionId}`, `PUT /pages/{id}/permissions/mode` |
| Profil | `GET/PUT /profile`, `POST /profile/password`, `GET/POST /profile/recovery` |
| Verwaltung (Admin) | `GET /pages/deleted`, `GET /admin/principals`, `GET/POST /admin/users`, `GET/PUT/DELETE /admin/users/{id}`, `GET/POST /admin/groups`, `PUT/DELETE /admin/groups/{id}` |

Seitenänderungen, Löschen, Verschieben und Wiederherstellen verlangen die aktuelle
`revision`; veraltete Stände werden mit `409` abgelehnt. Inhalte werden serverseitig
wie in der Web-UI bereinigt.

## Begleitende Korrekturen

- Migration `1.0.14-utf8mb4` stellt die Basistabellen von `utf8` (3 Byte) auf
  `utf8mb4` um. Vorher führten Emoji – auf Handytastaturen alltäglich – in Seiten,
  Kommentaren, Labels oder Namen zu einem Serverfehler. Die Umstellung baut die
  betroffenen Tabellen einmalig neu auf; bei großen Beständen ein Wartungsfenster
  einplanen.
- `GroupImpl.isGroupExist` nutzte einen ungültigen JPQL-Alias; das Anlegen von
  Gruppen über die REST-API schlug dadurch fehl.
- Der HTML-Sanitizer behält jetzt `pre` (Codeblock) und `hr` (Trennlinie). Beide bietet der
  Editor an, sie gingen bisher beim Speichern verloren.
- `UserDao.findById`/`GroupDao.findById` liefern für unbekannte IDs `null` statt einer
  `NullPointerException`, wie es die vorhandenen Aufrufer erwarten.

Die Integrationstests in `ClientApiIntegrationTest` prüfen Anmeldung, CSRF,
Berechtigungen, Revisionen, Anhänge, Freigaben, Verwaltung und Emoji-Speicherung
gegen eine echte MariaDB.
