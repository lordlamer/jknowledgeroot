# Editor und Frontend-Build

Stand: R08, 12. September 2026.

## Editorwechsel

Der alte CKEditor-Classic-Predefined-Build 40.1.0 wurde durch **Tiptap 3.31.3**
mit MIT-lizenzierten Erweiterungen ersetzt. Das Projekt behält seine BSD-2-Clause-Lizenz.
Die aktuelle CKEditor-Installation erfordert einen Lizenzschlüssel beziehungsweise
GPL-Nutzung. Für die Umsetzung wurde ein Editor mit freizügiger Lizenz gewählt;
es wird kein kommerzieller Dienst und kein Editor-Lizenzschlüssel benötigt.

Die gemeinsame Integration in `frontend/editor.js` ersetzt die unterschiedlichen
Editor-Konfigurationen von „New page“ und „Edit“. Sie bietet Überschriften, Fett,
Kursiv, Unterstreichen, Durchstreichen, Listen, Zitate, Codeblöcke, Links, Bilder
per URL, Tabellen, Ausrichtung sowie Rückgängig/Wiederholen. Bilddateien lassen
sich weiterhin als Seitenanhang hochladen; ein Inline-Uploadadapter ist nicht
Bestandteil dieses Schritts. Die zuvor konfigurierten Premium-Schaltflächen ohne
passende Plugins und die freie HTML-Vorschau entfallen.

Gespeichert wird weiterhin HTML in derselben Datenbankspalte. Es gibt keine
Inhaltsmigration beim Start. Der Editor übernimmt Überschriften, Listen, Tabellen,
Links, Bilder und unterstützte Formatierungen aus vorhandenen Inhalten.
Nicht unterstützte Spezialelemente können beim Bearbeiten normalisiert werden;
beliebiges HTML wird nicht unverändert weitergereicht. Vor einem Release sind
repräsentative eigene Bestandsinhalte zusätzlich zu den automatisierten Beispielen
zu prüfen.

DOMPurify 3.4.17 bereinigt vorhandene Inhalte vor der Editorinitialisierung und HTML
beim Einfügen. Skripte, Eventhandler und aktive Einbettungen werden entfernt.
Der serverseitige HTML Sanitizer bleibt die maßgebliche Prüfung beim Speichern;
clientseitige Bereinigung ersetzt keine Rechte- oder Inhaltsprüfung im Backend.

Änderungen werden in das Formularfeld übernommen. Beim HTMX-Request werden auch
die bereits erfassten Requestparameter aktualisiert. Dadurch enthält „Save changes“
den aktuellen Bearbeitungsstand. Beim Austausch eines Seitenfragments wird die
Editorinstanz zerstört und beim nächsten Öffnen neu erzeugt. Schlägt die
Initialisierung fehl, bleibt das ursprüngliche Textfeld verfügbar.

## Automatisches Speichern (R62)

Ein Formular mit `data-autosave` (das Bearbeitungsformular) schickt zwei
Sekunden nach der letzten Änderung Name, Labels, Inhalt und Revision per
`fetch` an `POST /ui/page/{id}/draft`; die Antwort nennt Zeitpunkt und
Bearbeiter, der Status steht in `[data-autosave-status]`. Der Inhalt wird
vorher wie beim Speichern mit DOMPurify bereinigt, der Server bereinigt ihn
erneut. Die Anfrage des Formulars selbst (Veröffentlichen) pausiert das
Autosave und bricht einen laufenden Aufruf ab, damit eine späte Antwort keine
eben veröffentlichte Arbeitskopie wieder anlegt; scheitert das Veröffentlichen
(etwa 409), läuft das Autosave weiter. Beim Verlassen des Editors wird ein
ausstehender Stand mit `keepalive` nachgereicht. Schlägt ein Autosave fehl,
bleiben die Eingaben im Editor und die Statuszeile meldet es. Fachliche
Beschreibung in [product-functions.md](product-functions.md#arbeitskopie-entwürfe-und-automatisches-speichern).

## Gemeinsames Bearbeiten (R63)

Die Tiptap-Erweiterungen Collaboration und Collaboration-Caret binden den
Editor an ein Yjs-Dokument; `@tiptap/y-tiptap`, `yjs` und `y-protocols`
(alle MIT) kommen ins Bundle. `LiveProvider` in `frontend/editor.js` ist
der eigene Transport: ein WebSocket je Editor zu `/ui/page/{id}/collab`,
binäre Frames mit einem Typbyte (Update, Awareness, Snapshot, Ready, Peer
joined, Snapshot requested, Published), beschrieben in
`CollaborationHandler`. Der
Server (`spring-boot-starter-websocket`) hängt Updates an das Log der
Arbeitskopie an und spielt es neuen Teilnehmern vor; beim Beitritt sagt
das Ready-Frame, ob der Browser das Dokument aus dem Formular befüllen
muss. Ab einem Megabyte Log fordert der Server den Schreiber zu einem
Snapshot auf, der die bisherigen Updates ersetzt. Awareness (Cursor,
Namen) wird nur weitergereicht. Die Undo-Historie von StarterKit ist im
Live-Modus abgeschaltet, Collaboration bringt ihre eigene mit.

Der Editor wird erst nach dem Ready-Frame erzeugt; bleibt die Verbindung
aus (acht Sekunden, fünf Verbindungsversuche), entsteht er ohne
Live-Modus aus dem Formularinhalt. Die Statusleiste (`.kr-editor-status`
in `edit.html`, gefüllt von `editor.js`) zeigt den Zustand der Sitzung
und je Mitbearbeiter einen Chip mit Initiale in der Cursorfarbe aus den
Awareness-Daten.

Veröffentlicht ein Browser mit Live-Sitzung (Formularfeld `live=true`,
gesetzt von `editor.js`) oder jemand die Arbeitskopie von der
Seitenansicht, bleibt das Log erhalten: `PageEditingService` löst
`WorkingCopyPublished` aus, der Handler sendet nach dem Commit das
Published-Frame (neue Revision und Login), und die Browser setzen ihr
Revisionsfeld um. Ein Autosave mit `live=true` und veralteter Revision
wird mit 409 beantwortet und vom Browser stillschweigend wiederholt, sobald
die neue Revision da ist. Inhalt von außerhalb des gemeinsamen Dokuments
(Client-API, MCP, Editor ohne Live-Sitzung, Verwerfen) löscht das Log und
schließt den Raum mit 4409; 4403 bedeutet Recht verloren. In beiden Fällen
wird der Editor schreibgeschützt, das automatische Speichern stoppt, und
eine Hinweisbox nennt den Grund (bei 4409 mit „Reload editor"). Das
automatische Speichern (R62) läuft sonst weiter, aber nur für
Transaktionen, die im eigenen Browser entstanden sind; fremde
Live-Änderungen speichert der Browser, der sie getippt hat.

## Build und Aktualisierung

Der Maven-Build installiert lokal unter `target/frontend` Node **24.21.0** und npm
**11.6.2**. Eine systemweite Node-Installation ist für `mvnw verify` nicht nötig.
Anschließend laufen `npm ci --ignore-scripts` und der esbuild-Build aus `frontend`.
Die Versionsvorgaben stehen in `package.json`; `package-lock.json` enthält den
gesamten Abhängigkeitsbaum und die Paketprüfsummen. Lockfile und Paketvorgaben
müssen zusammen aktualisiert werden.

Haupt- und Testkompilierung laufen in getrennten `javac`-Prozessen (`fork=true`).
Damit werden auch die jeweiligen Annotation-Prozessoren getrennt ausgeführt.

Das Bundle und CSS werden nach `target/generated-resources/static/resources/editor`
geschrieben und ins JAR übernommen. Der Browser lädt sie vom eigenen Server.
Generierte Dateien und `node_modules` werden nicht committet.
`THIRD-PARTY.txt` enthält die Lizenztexte sämtlicher im Bundle enthaltener Pakete;
fehlende Texte lassen den Build scheitern. `target/editor-metafile.json` zeigt,
welche Quelldateien esbuild eingebunden hat.

Ein Frontend-Update muss den vollständigen Build, Browserprüfung und den
[Abhängigkeitsscan](dependencies.md) bestehen. Der Scan prüft auch npm-Pakete aus
dem Lockfile einschließlich Buildwerkzeugen und optionalen Plattformpaketen.

## Browserprüfung

`ApplicationSmokeIT` startet das gebaute JAR gegen temporäre MariaDB-/MinIO-Container.
Playwright installiert bei Bedarf seine passende Chromium-Version und prüft Login,
Seitenerstellung, drei Bearbeitungs-/Speicherzyklen über HTMX, den Erhalt von
Tabellen/Formatierung/Links/Bildern, das Einfügen schädlichen HTMLs und einen echten
Multipart-Upload mit bytegleichem Download. JavaScript-Ausnahmen und fehlerhaft
geladene Skripte/Stylesheets lassen den Test scheitern.

Unter Linux benötigt Chromium Systembibliotheken. GitHub Actions installiert sie
vor dem Build. Für eine andere Linux-Testmaschine ist der entsprechende Befehl:

```sh
sh ./mvnw -B --no-transfer-progress org.codehaus.mojo:exec-maven-plugin:3.5.0:exec \
  -Dexec.executable=java -Dexec.classpathScope=test \
  '-Dexec.args=-classpath %classpath com.microsoft.playwright.CLI install-deps chromium'
```

Dies installiert Betriebssystempakete und kann Administratorrechte benötigen.
Unter Windows erfolgt kein solcher Systempaket-Schritt. Der erste Lauf benötigt
Netzwerkzugriff für Buildwerkzeuge, npm-Pakete, Container und Chromium.

Der Test blockiert externe Google-Font-Abfragen mit einer leeren CSS-Antwort;
deren Verfügbarkeit ist damit nicht geprüft. Editor-Code und Editor-CSS werden
aus dem tatsächlichen JAR geladen. Ein Screenshot entsteht unter
`target/editor-smoke.png`. Firefox, Safari, Mobilgeräte und vollständige
Barrierefreiheit bleiben zusätzliche Releaseprüfungen.

Quellen:

- [Tiptap: Vanilla-JavaScript-Integration](https://tiptap.dev/docs/editor/getting-started/install/vanilla-javascript)
- [Tiptap-Lizenz](https://github.com/ueberdosis/tiptap/blob/main/LICENSE.md)
- [CKEditor: Installation und Lizenzschlüssel](https://ckeditor.com/docs/ckeditor5/latest/getting-started/installation/self-hosted/quick-start.html)
- [Playwright: Browser und Systemabhängigkeiten](https://playwright.dev/java/docs/browsers)
