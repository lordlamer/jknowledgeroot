# MCP-Server für KI-Assistenten

Knowledgeroot bietet einen Server nach dem Model Context Protocol (MCP), über
den KI-Assistenten wie Claude (claude.ai, Claude Desktop, Claude Code) im Namen
eines Benutzers suchen, lesen und auf Wunsch schreiben. Der Assistent meldet
sich per OAuth 2.1 an; es gibt keine API-Schlüssel und keine zusätzlichen
Konten. Jeder Aufruf läuft mit den Space- und Seitenrechten des Benutzers, der
den Assistenten verbunden hat.

## Einschalten

1. Admin → Settings → **MCP server for AI assistants** aktivieren. Solange der
   Server aus ist, antworten `/mcp`, die OAuth-Routen und die
   Metadata-Dokumente mit `404`; bestehende Verbindungen enden damit sofort.
2. **Allowed assistant hosts** nennt die Hosts, zu denen ein Assistent nach
   der Anmeldung zurückgeleitet werden darf (Standard `claude.ai` und
   `*.claude.ai`). Assistenten auf dem Rechner des Benutzers (`localhost`,
   `127.0.0.1`, beliebiger Port) sind immer erlaubt. Andere Anbieter werden
   hier eingetragen, etwa `chatgpt.com`.
3. `KR_PUBLIC_URL` sollte die öffentliche HTTPS-Origin nennen (ohne Unterpfad).
   Sie wird als Aussteller und Zielgruppe in die Tokens geschrieben; ohne
   Angabe wird die Origin aus jedem Request abgeleitet, was hinter einem Proxy
   die korrekte `server.forward-headers-strategy` voraussetzt (siehe
   [production.md](production.md#https-proxy-vertrauen-und-cookies)).

Die Server-URL für Assistenten lautet `https://<host>/mcp`; die
Einstellungsseite zeigt sie an.

## Verbinden

- **claude.ai / Claude Desktop:** Settings → Connectors → *Add custom
  connector*, URL `https://<host>/mcp`, kein Client-ID/Secret nötig. Beim
  ersten Gebrauch öffnet sich der Knowledgeroot-Login (inklusive zweitem
  Faktor), danach die Zustimmungsseite.
- **Claude Code:** `claude mcp add --transport http knowledgeroot
  https://<host>/mcp`, danach `/mcp` in der Sitzung und *Authenticate*.
- Andere MCP-Clients mit OAuth-Unterstützung funktionieren, sobald ihr
  Rückkehr-Host freigegeben ist.

Auf der Zustimmungsseite entscheidet der Benutzer, ob der Assistent nur lesen
(`mcp:read`) oder auch schreiben darf (`mcp:write`: Seiten anlegen und
bearbeiten, Kommentare). Schreiben ist standardmäßig abgewählt. Wer später
schreiben möchte, verbindet den Assistenten neu und wählt es an; die
Zustimmung wird je Assistent gemerkt.

Im Profil unter **Connected assistants** sieht jeder Benutzer seine
Verbindungen (Name, Zugriff, Verbunden seit, gültig bis) und kann sie
trennen. Ein Passwortwechsel beendet alle Verbindungen des Benutzers;
deaktivierte oder gelöschte Konten verlieren den Zugriff mit dem nächsten
Aufruf.

## Werkzeuge

| Tool | Zugriff | Zweck |
|---|---|---|
| `list_spaces` | lesen | Betretbare Spaces mit id, key, name, description |
| `search_pages(query, space?, start?)` | lesen | Volltextsuche, 20 Treffer je Aufruf mit Pfad, Space und Ausschnitt |
| `get_page(id)` | lesen | Seite mit Breadcrumb, Labels, Inhalt als HTML, Revision und Link |
| `list_pages(parent?, space?)` | lesen | Unterseiten einer Seite oder Hauptseiten eines Space (50 je Aufruf) |
| `list_starred()` | lesen | Favoriten des Benutzers |
| `list_comments(page)` | lesen | Kommentare einer Seite |
| `create_page(name, content, parent?, space?, labels?)` | schreiben | Neue Seite unter einer Elternseite oder als Hauptseite |
| `update_page(id, revision, name?, content?, labels?)` | schreiben | Speichern mit Revisionsprüfung; ausgelassene Felder bleiben |
| `add_comment(page, text)` | schreiben | Kommentar im Namen des Benutzers |

Inhalte sind HTML wie im Editor; der Sanitizer entfernt Skripte und Stile
beim Speichern. Fehlermeldungen an den Assistenten sind feste Texte
(„You may not edit this page.", „The page has changed meanwhile…"); technische
Ursachen stehen nur im Serverlog. Löschen, Verschieben, Anhänge und
Rechteverwaltung gibt es bewusst nicht über MCP.

## Ablauf und Absicherung

- **Discovery:** `/.well-known/oauth-protected-resource/mcp` (RFC 9728) nennt
  den Autorisierungsserver (dieselbe Origin) und die Scopes;
  `/.well-known/oauth-authorization-server` (RFC 8414) nennt die Endpunkte
  `/oauth2/authorize`, `/oauth2/token`, `/oauth2/register`, `/oauth2/jwks`
  und `/oauth2/revoke`. Ein Aufruf von `/mcp` ohne Token antwortet `401` mit
  `WWW-Authenticate: Bearer resource_metadata="…"`.
- **Registrierung (RFC 7591):** Assistenten registrieren sich selbst unter
  `/oauth2/register` als öffentliche Clients ohne Secret. Rückkehr-URIs
  müssen `https` zu einem freigegebenen Host oder `http(s)` zu `localhost`
  sein; alle Clients erhalten dieselben Scopes, Zustimmungspflicht und
  Token-Laufzeiten.
- **Anmeldung:** Authorization Code mit PKCE (S256) über den normalen
  Formular-Login einschließlich zweitem Faktor; `/login/success` führt den
  OAuth-Ablauf fort. Zustimmungsseite `/oauth2/consent`.
- **Tokens:** Access-Tokens sind RS256-signierte JWTs mit 15 Minuten
  Laufzeit, Subjekt ist die Benutzer-ID, Zielgruppe `https://<host>/mcp`,
  Aussteller die Origin. Refresh-Tokens laufen 7 Tage und rotieren bei jeder
  Verwendung; ein zweiter Gebrauch wird abgelehnt. Öffentliche
  Clients erneuern und widerrufen (`/oauth2/revoke`) mit ihrer Client-ID
  allein; das Token selbst ist das Geheimnis. Die Signaturschlüssel
  liegen in `oauth2_signing_key`, der private Teil mit `KR_MASTER_KEY`
  verschlüsselt, damit alle Instanzen dieselben Schlüssel nutzen.
- **Prüfung je Aufruf:** Signatur, Laufzeit, Aussteller und Zielgruppe,
  dann das Konto (aktiv, nicht Gast) und die Passwortmarke im Token; weicht
  sie ab, sind Access- und Refresh-Token ungültig.
- **Zustand:** `/mcp` arbeitet zustandslos (Streamable HTTP ohne
  Sitzungen), ohne Cookies und ohne CSRF; mehrere Instanzen hinter einem
  Load-Balancer brauchen keine Sticky Sessions. Tool-Aufrufe laufen im
  Request-Thread mit dem Security-Kontext des Tokens.
- **Aufräumen:** Stündlich werden Autorisierungen gelöscht, deren Tokens
  seit einem Tag abgelaufen sind, und Registrierungen ohne Autorisierung,
  die älter als eine Woche sind.

Tabellen: `oauth2_registered_client`, `oauth2_authorization`,
`oauth2_authorization_consent`, `oauth2_signing_key` (Changeset `1.0.17-mcp`).
Einstellungen: `mcp.enabled`, `mcp.redirect-hosts` in `app_setting`.

## Prüfung

`McpIntegrationTest` läuft gegen einen echten Port mit MariaDB: Server aus
(404), Einschalten und Validierung der Host-Liste, Metadata-Dokumente und
`WWW-Authenticate`, Registrierung (fremder Host und vertrauliche Clients
abgelehnt, `localhost` und freigegebener Host angenommen), der vollständige
Ablauf über Login und Zustimmung, Leser (lesen ja, schreiben nein),
Außenstehender (Seite unsichtbar), Bearbeiter mit Schreibrecht (Anlegen mit
Sanitizer, Revisionskonflikt, Speichern, Kommentar, Unterseiten), Refresh mit
Rotation, Profilanzeige und Trennen, Passwortwechsel (401 und
`invalid_grant`), Ausschalten. `McpSettingsTest` deckt die Rückkehr-Allow-List
ab. Alle Aufrufe im Test nutzen den MCP-Java-SDK-Client wie echte Assistenten.
