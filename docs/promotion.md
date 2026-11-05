# Bekanntmachung und Kanäle

Stand: 5. November 2026 (R58). Diese Seite hält fest, wo Knowledgeroot angekündigt
wird, in welcher Reihenfolge, und welche Texte dafür verwendet werden. Freshmeat
beziehungsweise Freecode nimmt seit Juni 2014 keine Releases mehr an; die Rolle
übernehmen heute GitHub selbst, kuratierte Listen und die Self-Hosting-Communities.

## Voraussetzungen vor der ersten Ankündigung

- [x] README produktorientiert: Pitch, Screenshots, Featureliste, Compose-Schnellstart
  (R58).
- [x] Screenshots unter `docs/screenshots/`, reproduzierbar mit
  `scripts/screenshots/seed.sh` und `scripts/screenshots/shoot.mjs`.
- [ ] GitHub-Repository: Beschreibung, Homepage, Topics und Social-Preview-Bild
  (siehe unten; Social Preview nur über die Weboberfläche).
- [ ] Öffentliche Demo-Instanz mit Gastzugang, nächtlich zurückgesetzt (optional,
  erhöht die Aufnahmechance bei awesome-selfhosted und die Resonanz bei Hacker News
  deutlich).
- [ ] Stabiles Release **1.1.0** statt eines Kandidaten: Die Features, mit denen
  geworben wird (Spaces, 2FA, MCP), sind erst in der 1.1-Linie enthalten.
- [ ] Screenshots nach dem 1.1.0-Tag erneut aufnehmen (das rc.1-Image zeigt die
  Editor-Toolbar noch deutsch; R57 ist dort nicht enthalten) und den Editor als
  Bild ergänzen.

## GitHub-Metadaten

Mit der GitHub-CLI (`gh auth login` einmalig), aus dem Repository heraus:

```bash
gh repo edit lordlamer/jknowledgeroot --description "Self-hosted knowledge base for teams: spaces, per-page permissions, page history, two-factor sign-in and an MCP server for AI assistants. Java, Spring Boot, MariaDB." --homepage "https://knowledgeroot.org"
```

```bash
gh repo edit lordlamer/jknowledgeroot --add-topic knowledge-base,wiki,self-hosted,knowledge-management,documentation,java,spring-boot,htmx,mariadb,docker,mcp,mcp-server,confluence-alternative
```

Social Preview: *Settings → General → Social preview*, Datei
`docs/screenshots/social-preview.png` (1280 × 640) hochladen. Außerdem unter
*Settings → General → Features* die Discussions prüfen; Fragen landen sonst als Issues.

## Kanäle in der Reihenfolge der Ankündigung

| Reihenfolge | Kanal | Was | Wiederholung |
|---|---|---|---|
| 1 | [awesome-selfhosted](https://github.com/awesome-selfhosted/awesome-selfhosted-data) | Pull Request mit `software/knowledgeroot.yml` (unten), Kategorie *Wikis* oder *Knowledge Management Tools*. Bedingungen: OSI-Lizenz, Projekt mindestens einige Monate alt, aktiv gepflegt, kein Kandidat als einzige Version. | einmalig; Datenpflege bei Änderungen |
| 2 | [selfh.st](https://selfh.st) | Newsletter-Einreichung (neue App und später jedes Release) über das Formular der Seite. | jedes Release |
| 3 | [AlternativeTo](https://alternativeto.net) | Eintrag als Alternative zu Confluence, BookStack, Wiki.js, DokuWiki, Outline. | einmalig |
| 4 | r/selfhosted, r/opensource | Vorstellung mit Screenshots, Flair *Release*/*Software Dev*, Feedback erbitten. Nicht am selben Tag wie Hacker News. | nur größere Releases |
| 5 | Hacker News (*Show HN*) | Titel unten; der MCP-Server mit OAuth 2.1 ist der Aufhänger. Kein Cross-Posting am selben Tag, auf Kommentare antworten. | einmalig |
| 6 | Lobste.rs, Mastodon (#selfhosted #opensource #java), Bluesky | Kurzfassung mit Link und einem Screenshot. | jedes Release |
| 7 | MCP-Verzeichnisse: [offizielle MCP Registry](https://github.com/modelcontextprotocol/registry), PulseMCP, Glama, mcp.so | Knowledgeroot als Remote-MCP-Server mit OAuth eintragen; Server-URL ist je Installation `https://<host>/mcp`, deshalb als „self-hosted remote server" mit Verweis auf [mcp.md](mcp.md). | einmalig |
| 8 | App-Kataloge: Unraid Community Apps, Cloudron, YunoHost, CasaOS, Umbrel, Portainer-Templates | Je Katalog ein Paket/Template auf Basis von `deploy/compose.production.yaml`; bringt stetige Installationen ohne Ankündigung. | je Katalog einmalig, danach Versionspflege |
| 9 | Deutschsprachig: heise Software-Verzeichnis, administrator.de, Computerbase-Forum (Self-Hosting) | Deutscher Text unten. Pro-Linux existiert seit 2022 nicht mehr. | größere Releases |
| 10 | [freshcode.club](https://freshcode.club) | Nachbau von Freshmeat, noch online, geringe Reichweite; Eintrag kostet wenige Minuten. | jedes Release |

Faustregeln: Eine Ankündigung pro Kanal und Release, auf Rückfragen am Tag der
Veröffentlichung antworten, Kritik an fehlenden Funktionen als Issues aufnehmen und
im Thread verlinken. Reddit und Hacker News nur mit einer stabilen Version und
einem funktionierenden Fünf-Minuten-Einstieg (README-Schnellstart) bespielen.

## Texte

### Kurzbeschreibung (Verzeichnisse, maximal 160 Zeichen)

> Self-hosted knowledge base for teams: spaces, per-page permissions, page history,
> two-factor sign-in and an MCP server for AI assistants. Java and MariaDB.

### awesome-selfhosted (`software/knowledgeroot.yml`)

```yaml
name: "Knowledgeroot"
website_url: "https://knowledgeroot.org"
source_code_url: "https://github.com/lordlamer/jknowledgeroot"
description: "Team knowledge base with spaces, page trees, per-page permissions, version history and comparison, attachments, full-text search, two-factor sign-in and an MCP server that lets AI assistants read and write with the user's own permissions."
licenses:
  - BSD-2-Clause
platforms:
  - Java
  - Docker
tags:
  - Wikis
# demo_url: "https://demo.knowledgeroot.org"   # sobald eine Demo läuft
```

### Show HN

Titel (maximal 80 Zeichen):

> Show HN: Knowledgeroot – self-hosted team wiki with an OAuth-secured MCP server

Text:

> Knowledgeroot is a self-hosted knowledge base for teams: pages in a tree inside
> spaces, per-page permissions, full version history with side-by-side comparison,
> attachments, search, two-factor sign-in. Java, Spring Boot and MariaDB, one Docker
> image, BSD-2-Clause.
>
> The part I would like feedback on is the MCP server. Claude (or any MCP client with
> OAuth support) connects to `https://host/mcp`, signs in as the user through the normal
> login including the second factor, and gets a consent page: read only, or also write.
> Every tool call runs with that user's space and page permissions, so an assistant
> cannot see more than its owner. Administrators get an audit log of every call and
> can cut connections; users see and revoke their assistants in the profile. No API
> keys, no service accounts.
>
> Quick start with Docker Compose is in the README; the repository also documents
> backup, restore and the release process with checksums. I started the Java rewrite
> of my much older PHP knowledge base in 2017 and have been using it for my own
> documentation since. Happy to answer questions about the permission model or the
> OAuth flow.

### r/selfhosted

Titel:

> Knowledgeroot: self-hosted team knowledge base with spaces, page history, 2FA and an
> MCP server for AI assistants (Java, MariaDB, BSD-2-Clause)

Text: Die ersten beiden Absätze des Show-HN-Textes, danach:

> What it is not (yet): no real-time collaborative editing, no Markdown source mode
> (the editor is rich text, content is stored as sanitised HTML), no LDAP/OIDC login
> yet. I would like to hear which of those matters most to you.
>
> Docker Compose quick start, screenshots and docs: <https://github.com/lordlamer/jknowledgeroot>

### Mastodon / Bluesky (unter 500 Zeichen)

> Knowledgeroot 1.1: a self-hosted team knowledge base in Java with spaces, per-page
> permissions, version history, 2FA and an MCP server that lets Claude read and write
> your wiki with your own permissions, OAuth 2.1, no API keys. One Docker image,
> BSD-2-Clause. https://github.com/lordlamer/jknowledgeroot #selfhosted #opensource
> #java #mcp

### Deutsch (heise, Foren)

> Knowledgeroot ist eine selbst gehostete Wissensdatenbank für Teams: Seiten in einem
> Baum innerhalb von Spaces, Rechte je Space und Seite, vollständige Versionshistorie
> mit Vergleich, Anhänge, Volltextsuche, Zwei-Faktor-Anmeldung und sechs
> Oberflächensprachen. Neu in 1.1 ist ein MCP-Server, über den KI-Assistenten wie
> Claude per OAuth 2.1 mit den Rechten des jeweiligen Benutzers lesen und, nach
> gesonderter Zustimmung, schreiben; Administratoren sehen jeden Aufruf im
> Audit-Log. Java 25, Spring Boot 4 und MariaDB, ein Docker-Image, BSD-2-Clause.
> Schnellstart mit Docker Compose und Dokumentation:
> <https://github.com/lordlamer/jknowledgeroot>

## Release-Routine

Nach jedem Tag mit veröffentlichten Artefakten: GitHub-Release-Notes prüfen,
selfh.st einreichen, Mastodon/Bluesky-Kurzfassung posten, freshcode.club
aktualisieren, Versionsangaben in den App-Katalogen nachziehen. Hacker News und
Reddit nur bei einer neuen Hauptversion.
