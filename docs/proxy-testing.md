# HTTPS-Proxy lokal prüfen

Der Test führt das vorhandene [nginx-Beispiel](../deploy/nginx.conf.example)
vor dem Produktions-Compose aus. Er ergänzt die direkten TLS-/Tomcat-Tests um
die gesamte Strecke vom HTTPS-Client über nginx zur Anwendung.

Nach dem Build beider Images gemäß [release.md](release.md):

```sh
target/frontend/node/node deploy/proxy-smoke-test.mjs knowledgeroot:verified knowledgeroot:database-verified
```

Unter Windows `target/frontend/node/node.exe` verwenden. Docker mit Compose und
`openssl` müssen im PATH verfügbar sein; Git für Windows liefert OpenSSL mit.
Der Test benötigt keine Domain, Zugangsdaten oder bestehenden Volumes. Alle
veröffentlichten Ports liegen auf Loopback und werden dynamisch vergeben.

Der Helfer erstellt ein zufälliges Compose-Projekt mit eigenen Konten, Datenbank-
und Dateivolumes sowie einem für einen Tag gültigen Testzertifikat. Der Client
verifiziert Zertifikat und Hostname ausschließlich gegen dieses Zertifikat;
die TLS-Prüfung wird nicht global deaktiviert. `knowledge.example.org` dient nur
als TLS-/HTTP-Name, alle Verbindungen gehen direkt an `127.0.0.1`.

Der nginx-Testcontainer verwendet das offizielle Image **1.30.4-alpine**, gepinnt
auf `sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c`.
Die stabile Version ist im [nginx-Downloadverzeichnis](https://nginx.org/en/download.html)
dokumentiert. Das ist eine Testabhängigkeit; die Auswahl und Sicherheitsprüfung
des tatsächlichen Produktionsproxys bleibt Teil der Betriebsabnahme.

Aus dem Beispiel werden ausschließlich Zertifikatspfade und die interne
Upstream-Adresse für das Testnetz angepasst. Die Header- und Redirect-Regeln
bleiben erhalten. Der Container läuft zunächst ohne nginx, damit seine tatsächliche
IP-Adresse als einzelne vertrauenswürdige Adresse in der Anwendung eingetragen
werden kann. Nach dem App-Start prüft `nginx -t` die Konfiguration und startet nginx.
Der Proxy erhält nur Testzertifikat und Konfiguration, keine Datenbankpasswörter.

Geprüft werden:

- HTTP-Weiterleitung auf den festen HTTPS-Host mit unverändertem Pfad und Query.
- Zertifikatsprüfung, HSTS und erfolgreicher Administratorlogin über nginx.
- Secure-/HttpOnly-/SameSite=Lax-Sitzungscookies.
- Ablehnung von Logout ohne CSRF-Token und Sitzungsende nach gültigem Logout.
- HTTPS-Weiterleitungen trotz gefälschter Host-, Forwarded- und X-Forwarded-Header.
- Ignorierte HTTPS-Behauptung bei direktem Zugriff außerhalb der vertrauten Proxy-Adresse.
- Gemeinsame IP-Quote trotz wechselnder gefälschter Client-IP: Nach einem gültigen
  und zwei ungültigen Logins wird auch ein weiterer Login mit richtigem Passwort
  abgelehnt. Nur im Test gilt eine IP-Quote von drei Versuchen in zehn Minuten;
  unterschiedliche Loginnamen vermeiden eine Verwechslung mit der Kontenquote.

Die [nginx-Dokumentation zu proxy_set_header](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_set_header)
beschreibt das Ersetzen und Entfernen eingehender Header.

Nach Erfolg enthält `target/proxy-smoke.json` Prüfergebnis, Image-IDs, die feste
Proxyreferenz und die Prüfsumme der getesteten Beispielkonfiguration. Alte
Erfolgsberichte werden vor einem neuen Lauf entfernt. Das eigene Projekt samt
Volumes und Testschlüsseln wird bei Ende aufgeräumt; ein harter Prozess-/Hostausfall
kann wie bei anderen Integrationstests eine manuelle Bereinigung erfordern.

Der Workflow führt die Prüfung nach dem Imagebuild aus. Ein erfolgreicher lokaler
Test ersetzt weder ein öffentlich gültiges Zertifikat noch die Kontrolle von
Firewall, tatsächlicher Proxy-Adresse, Erneuerung und Alarmierung am Zielhost.
Diese Punkte bleiben im [Abnahmeprotokoll](operational-acceptance.md) offen.
