# Speichern und Fehlerbehandlung

Stand: R11, 12. September 2026.

## Seiten

`PageCreationService` speichert neue Seiten, Anfangsrechte und Labels bereits seit
R05 gemeinsam. `PageEditingService` umfasst jetzt auch die Bearbeitung einer
bestehenden Seite: Inhalt und Änderungs-Audit, Ersetzen der Labels sowie sämtliche
mitgesendeten Rechteänderungen bilden eine Datenbanktransaktion. Ein Fehler in
einem dieser Schritte rollt auch die vorherigen Änderungen zurück. Ungültige
Rechtelöschungen werden abgelehnt und nicht mehr stillschweigend übersprungen.

Der Service prüft Bearbeitungsrechte innerhalb der Transaktion. Rechteänderungen
verlangen weiterhin einen Administrator und lokale Seitenrechte; die vereinbarte
Vererbung und die Regeln für Gastseiten bleiben bestehen. Einzelne Requests zur
Rechteverwaltung oder zum Wechsel des Vererbungsmodus sind weiterhin jeweils
eigenständige Vorgänge.

Das normale Bearbeitungsformular ändert Name, Inhalt und Labels. Erstellungsdaten,
Elternseite, Zeitplanung und Aktivierungs-/Löschstatus werden dabei beibehalten.
Der REST-Updateweg verwendet ebenfalls den Service und kann seine bisherigen
Zeitplanungs-/Statusfelder ändern. Die Änderungsidentität und der Zeitpunkt stammen
vom Server; eine mitgesendete fremde Benutzer-ID wird nicht übernommen. Die
REST-Antwort enthält diese tatsächlichen Auditwerte und die erhaltenen
Erstellungsdaten. Bei Gästen wird als Benutzer-ID `NULL` gespeichert.

Die Transaktion verhindert teilweise gespeicherte Vorgänge. Sie erkennt noch
keine veralteten Bearbeitungsstände und ersetzt keine Versionsprüfung gegen
gleichzeitiges Überschreiben; das bleibt R15.

## Dateien

UI und REST verwenden gemeinsam `FileUploadService`. Er prüft das Bearbeitungsrecht
und sämtliche Dateien eines Requests vor dem ersten Schreibvorgang. Alle
Metadatensätze eines Mehrdatei-Uploads werden in einer Datenbanktransaktion
veröffentlicht. Ein leerer Upload wird abgelehnt; ein Speicher-, Lese- oder
Datenbankfehler führt zum Rollback des gesamten Requests. Frühere, bereits
erfolgreiche Requests bleiben erhalten. Die Erfolgsmeldung erfolgt erst nach
Rückkehr aus dem transaktionalen Service und damit nach dem Commit.

Dateiinhalte werden vor ihren Metadaten gespeichert. Die Auditfelder enthalten
die angemeldete Benutzer-ID, bei Gastuploads `NULL`; der bisher feste Wert `1`
entfällt. Eingabestreams werden geschlossen. Technische Fehlerdetails werden beim
UI-Upload protokolliert und nicht an die Fehlermeldung für den Benutzer angehängt.

Der lokale Treiber schreibt in eine temporäre Datei im selben Speicherverzeichnis
und verschiebt sie erst nach vollständigem Schreiben atomar zum endgültigen Pfad.
Ein abgebrochener Upload hinterlässt dadurch keine sichtbare Teil-Datei, die beim
nächsten Versuch als vorhandener Inhalt wiederverwendet würde. Das Dateisystem
muss atomare Verschiebungen innerhalb dieses Verzeichnisses unterstützen;
andernfalls schlägt der Upload fehl. Temporäre Dateien werden bei normalen
Fehlern entfernt; ein Prozessabbruch kann temporäre Reste hinterlassen.

## Grenze zwischen Datenbank und Storage

Dateisystem und Object Store nehmen nicht an der Datenbanktransaktion teil.
Schlägt die Datenbank nach erfolgreichem Speichern eines Objekts fehl, kann ein
vollständiges, nicht referenziertes Objekt zurückbleiben. Es wird weder als
erfolgreicher Anhang angezeigt noch automatisch gelöscht. Hash-Objekte können von
mehreren Anhängen und gleichzeitig laufenden Uploads gemeinsam verwendet werden;
eine Löschung beim Rollback könnte deren Inhalte entfernen. Dasselbe gilt für
einen Speicherfehler mit unklarem Ausgang nach vollständiger Übertragung.

Das manuelle Wartungsverfahren in [storage.md](storage.md) verlangt einen
Abgleich mit sämtlichen Datenbankreferenzen bei gestoppten Writern, Backup und
Quarantäne vor endgültiger Löschung. Eine automatische Bereinigung ist nicht
implementiert. Seit R11 werden neue Inhalte mit begrenztem Puffer gehasht und
unter SHA-256-Schlüsseln gespeichert; alte MD5-Objekte bleiben lesbar. Uploadgrenzen,
sichere Downloadheader und die unabhängige Treiberinitialisierung sind ebenfalls
dort beschrieben. Der Storage-Dienst für den produktiven Betrieb bleibt eine
offene Entscheidung in R12.

## Nachweise

Die Datenbanktests verwenden isolierte MariaDB-Container und echte
Transaktions-Proxys. Sie prüfen Rollback nach Labelersetzung, nach einer Folge von
Rechteänderungen, bei einer fremden/nicht vorhandenen Rechte-ID, beim späteren
Speicherfehler eines Upload-Batches und beim Metadatenfehler nach erfolgreicher
Dateiablage. Erfolgsfälle kontrollieren Inhalte, Rechte, Labels, Auditwerte und
erhaltene Erstellungs-/Zeitplanungsfelder.

Weitere Prüfungen decken leere oder unberechtigte Uploads vor dem ersten Schreiben,
Gast-Auditwerte, unterbrochenes lokales Schreiben mit erfolgreichem Wiederholen,
gefälschte REST-Auditwerte und lückenhafte Indizes neu hinzugefügter Formularrechte
ab. Der bestehende JAR-/Browsertest prüft zusätzlich Bearbeiten und einen echten
MinIO-Upload samt Download. Gehostete CI, Prozessabbruch während eines Commits,
gemischter Versionsbetrieb und umfassende Storage-Störungen sind nicht damit
nachgewiesen.
