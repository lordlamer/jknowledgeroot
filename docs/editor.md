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

DOMPurify 3.4.15 bereinigt vorhandene Inhalte vor der Editorinitialisierung und HTML
beim Einfügen. Skripte, Eventhandler und aktive Einbettungen werden entfernt.
Der serverseitige HTML Sanitizer bleibt die maßgebliche Prüfung beim Speichern;
clientseitige Bereinigung ersetzt keine Rechte- oder Inhaltsprüfung im Backend.

Änderungen werden in das Formularfeld übernommen. Beim HTMX-Request werden auch
die bereits erfassten Requestparameter aktualisiert. Dadurch enthält „Save changes“
den aktuellen Bearbeitungsstand. Beim Austausch eines Seitenfragments wird die
Editorinstanz zerstört und beim nächsten Öffnen neu erzeugt. Schlägt die
Initialisierung fehl, bleibt das ursprüngliche Textfeld verfügbar.

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
