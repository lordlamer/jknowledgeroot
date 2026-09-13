# Dateispeicher und Uploads

Stand: R11, 12. September 2026.

## Konfiguration

`KR_STORAGE_DRIVER=file` aktiviert ausschließlich den lokalen Dateispeicher.
`KR_FILE_STORAGE_DIR` bestimmt sein Verzeichnis (Standard `./storage`, relativ zum
Arbeitsverzeichnis). MinIO-Einstellungen werden dabei weder ausgewertet noch wird
MinIO kontaktiert. Das Verzeichnis wird beim Start angelegt und muss für den
Anwendungsbenutzer lesbar und schreibbar sein. Es gehört auf ein persistentes
Volume außerhalb des öffentlich ausgelieferten Webverzeichnisses. Atomare
Verschiebungen innerhalb dieses Verzeichnisses müssen unterstützt werden.

`KR_STORAGE_DRIVER=minio` bleibt der Standard. Dafür werden `KR_MINIO_URL`,
`KR_MINIO_ACCESS_KEY`, `KR_MINIO_SECRET_KEY` und `KR_MINIO_BUCKET` benötigt.
URL und Bucket haben derzeit die Vorgaben `http://localhost:9000` und
`knowledgeroot`; Zugangsdaten haben keinen Standardwert. Der Treiber prüft den
Bucket beim Start und legt ihn bei Bedarf an. Ein vorhandener Bucket benötigt
keine Berechtigung zur Bucket-Erstellung. Der lokale Speichertreiber wird dabei
nicht initialisiert. Unbekannte Treibernamen verhindern den Start.

Ein Wechsel des Treibers verschiebt keine Dateien. Vor einem Wechsel müssen
sämtliche referenzierten Objekte unter unveränderten Schlüsseln vollständig in
den neuen Speicher kopiert und ihre Inhalte geprüft werden. Seit R12 ist lokaler
Speicher für eine einzelne Instanz der dokumentierte Standard in
[production.md](production.md). Ein externer S3-Dienst für mehrere Instanzen
benötigt eine eigene Auswahl und Abnahme; das gepinnte MinIO-Testimage bleibt
eine Kompatibilitätsfixture.

## Grenzen und Ressourcen

| Umgebungsvariable | Standard | Bedeutung |
| --- | --- | --- |
| `KR_UPLOAD_MAX_FILE_SIZE` | `25MB` | Höchstens 25 MiB pro Datei |
| `KR_UPLOAD_MAX_REQUEST_SIZE` | `100MB` | Höchstens 100 MiB pro HTTP-Request einschließlich Multipart-Overhead |
| `KR_UPLOAD_MAX_FILES` | `10` | Höchstens zehn Dateien pro Upload-Request |

Die Einzeldateigrenze muss positiv und wegen der bestehenden Größen-Spalte
höchstens 2.147.483.647 Bytes sein. Die Requestgrenze muss mindestens so groß sein;
die Dateianzahl darf zwischen 1 und 100 liegen. Ungültige Grenzen verhindern den
Start. Für einen Upload genau an der Einzeldateigrenze muss die Requestgrenze
zusätzlich Platz für Formularfelder und Multipart-Header bieten. Proxy-Grenzen
müssen dazu passen. Größen-/Anzahllimits liefern HTTP 413; leere Dateien,
ungültige Namen und ungültige MIME-Angaben HTTP 400. Berechtigungen gelten
unverändert für UI und API.

Der Servlet-Container schreibt Multipart-Inhalte ab dem ersten Byte auf Disk.
Die Anwendung prüft den gesamten Batch vor dem ersten Speichern und liest jeden
Dateiinhalt zum SHA-256-Hashen mit einem 64-KiB-Puffer in eine eigene temporäre
Datei. Tatsächlich gelesene Bytes werden ebenfalls begrenzt und müssen zur
gemeldeten Größe passen. Genau diese zwischengespeicherten Bytes werden danach
gespeichert. MinIO verwendet zusätzlich begrenzte SDK-Puffer für 10-MiB-Teile.
Es gibt keinen anwendungsseitigen Aufruf von `MultipartFile.getBytes()` mehr.

Temporärer Speicher muss für parallele Requests ausgelegt sein: Servlet-Spooling,
Hash-Zwischenspeicher und beim lokalen Treiber eine weitere temporäre Kopie
können gleichzeitig Platz beanspruchen. Der Hash-Zwischenspeicher liegt in
`java.io.tmpdir` (`-Djava.io.tmpdir=...` vor `-jar`); das Servlet-Verzeichnis kann
über `spring.servlet.multipart.location` gesetzt werden. Diese Verzeichnisse
müssen privat und schreibbar sein. Es gibt keine globale Upload-Parallelitätsquote;
Kapazität und Lastgrenzen sind vor dem Release zu prüfen.

## Schlüssel, Downloads und Fehler

Neue Objekte heißen `sha256-` gefolgt von 64 kleinen Hex-Zeichen. Changeset
`1.0.10` erweitert die Hash-Spalte und erlaubt UTF-8-Dateinamen einschließlich
Emoji. Vorhandene MD5-Schlüssel mit 32 kleinen Hex-Zeichen bleiben unverändert
lesbar. Derselbe Inhalt kann deshalb zunächst sowohl unter einem alten als auch
unter einem neuen Schlüssel existieren. Es erfolgt keine automatische Umschreibung.
Ein Rollback auf eine ältere Anwendung darf nicht ungeprüft erfolgen: Frühere
Versionen können beim erneuten Upload nur MD5 erzeugen; ein Rückbau der Hash-Spalte
würde neue Schlüssel abschneiden. Datenbank und Objekte gemeinsam sichern und
einen Versionswechsel zunächst mit einer Kopie erproben.

Dateinamen werden auf den Basisnamen begrenzt; Client-Pfade werden entfernt,
Steuerzeichen und Namen über 255 Zeichen abgelehnt. Namen dienen nie als
Speicherpfad. Beide Treiber akzeptieren ausschließlich die beschriebenen
Objektschlüssel; lokale symbolische Objektdateien werden abgelehnt.

Downloads werden als Attachment mit UTF-8-kodiertem Dateinamen,
`X-Content-Type-Options: nosniff` und `Cache-Control: private, no-store`
ausgeliefert. Ungültige Bestandsnamen werden durch `download`, ungültige MIME-Werte
durch `application/octet-stream` ersetzt. MIME-Angaben stammen vom Client;
es gibt keine Inhaltsprüfung oder Virensuche.

Fehlende Objekte ergeben 404, nicht erreichbarer/verweigerter Speicher 503.
MinIO behandelt ausschließlich `NoSuchKey` als fehlendes Objekt, nicht etwa
fehlende Buckets oder Zugriffsfehler. Verbindungen haben 10 Sekunden Connect-,
60 Sekunden Read-/Write- und zwei Minuten Call-Timeout. Bricht ein bereits
begonnener Download ab, wird kein Fehlertext an seine Dateibytes angehängt;
der Client muss die unvollständige Übertragung anhand der Länge erkennen.

Vollständige Objekte werden vor dem Commit der Metadaten gespeichert. Gleichzeitige
Uploads desselben Inhalts dürfen dasselbe Objekt gemeinsam verwenden. Ein
fehlgeschlagener Batch rollt seine Metadaten zurück; vollständige Objekte werden
aus Rücksicht auf andere Referenzen nicht beim Rollback gelöscht. Details stehen
in [transactions.md](transactions.md).

## Sichere manuelle Bereinigung

Es gibt bewusst noch keinen automatischen Garbage Collector. Für eine manuelle
Bereinigung gilt dieses Wartungsverfahren:

1. Sämtliche Anwendungsinstanzen und weiteren Writer stoppen. Laufende Uploads
   müssen beendet sein. Während Inventarisierung und Quarantäne dürfen keine
   neuen Schreibvorgänge starten. Alter allein schützt nicht vor einem parallelen
   Upload, der ein älteres Objekt gerade wiederverwendet.
2. Datenbank und vollständigen Speicher zusammen sichern. Alle Referenzen mit
   `SELECT DISTINCT hash FROM file` exportieren, **einschließlich logisch
   gelöschter Anhänge**. Nicht nach `deleted` oder Seitenstatus filtern. Wird ein
   Bucket/Verzeichnis von mehreren Datenbanken geteilt, müssen alle Referenzmengen
   und Writer berücksichtigt werden; im Zweifel nicht bereinigen.
3. Die vollständige Objektliste mit dieser Referenzmenge vergleichen. Fehlende
   referenzierte Objekte als Konsistenzfehler untersuchen und aus dem Backup
   wiederherstellen. Unbekannte Schlüssel/Dateien weder umbenennen noch löschen.
4. Nur unreferenzierte gültige Hash-Objekte als Kandidaten behandeln. Vor dem
   Verschieben erneut gegen die unveränderte Datenbank prüfen. Kandidaten zuerst
   in ein separates Quarantäneverzeichnis/einen separaten Bucket kopieren,
   Byteanzahl und SHA-256-Prüfsumme vergleichen und ein Manifest mit ursprünglichem
   Schlüssel und Datum sichern. Erst danach das Original entfernen. Auch bei
   MD5-Objekten kann die Kopie mit SHA-256 verglichen werden; den Schlüssel erhalten.
5. Lokale `.upload-*.tmp` und `.health-*.tmp` im dedizierten Objektverzeichnis und
   `knowledgeroot-upload-*.tmp` im dedizierten JVM-Tempverzeichnis stammen von
   abgebrochenen Uploads. Nach Ausschluss sämtlicher Writer ebenfalls zunächst
   quarantänisieren. Keine fremden Dateien aus einem gemeinsam genutzten Tempordner
   entfernen. Unvollständige S3-Multipart-Uploads getrennt mit den Werkzeugen des
   Speicheranbieters auflisten und erst nach derselben Stilllegung abbrechen.
6. Anwendung starten und repräsentative alte/neue Anhänge herunterladen.
   Quarantäne und Manifest mindestens bis zum nächsten erfolgreich geprüften
   vollständigen Backup behalten, mit einer betrieblich festgelegten Frist von
   beispielsweise 30 Tagen. Erst danach endgültig entfernen.

Dieses Verfahren legt die notwendigen Schutzbedingungen fest. Eine automatisierte
Bereinigung sowie eine praktisch erprobte vollständige Wiederherstellung bleiben
gesonderte Arbeit; Backup und Restore werden in R14 abgenommen.

## Nachweise

Isolierte Tests prüfen beide Treiber mit 11-MiB-Inhalten, parallelem Speichern,
fehlenden Objekten und einem verschwundenen MinIO-Bucket. MariaDB-Tests prüfen
alte/neue Schlüssel, unveränderte Bestandsreferenzen beim Upgrade, gemeinsam
genutzte Objekte bei parallelen Transaktionen und Rollback nach Speicher-/DB-Fehlern.
Unit-Tests prüfen Grenzen, tatsächliche Streamlänge, temporäre Bereinigung,
Pfad-/Header-Manipulation und abgebrochene Downloads. Der JAR-Test startet die
Anwendung zusätzlich mit lokalem Speicher und ungültiger MinIO-URL und prüft über
HTTP Upload/Download an der Grenze sowie 413 direkt oberhalb der Grenze.
Ein harter Prozessabbruch, produktive Last und jedes mögliche S3-Ausfallszenario
werden dadurch nicht nachgewiesen.
