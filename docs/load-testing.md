# Wiederholbare Lastszenarien für Lesezugriffe

`deploy/load-smoke-test.mjs` erstellt eine eigene lokale Compose-Installation mit
synthetischen Daten. Es nimmt ausschließlich zwei lokale Images entgegen, keine
Ziel-URL oder Betreiberkonfiguration. Nach dem Lauf werden dieses Testprojekt,
seine Volumes und seine generierten Zugangsdaten entfernt. Bestehende Dienste
werden nicht als Testziel verwendet.

## Ausführen

Docker und die vom Maven-Build bereitgestellte Node-Laufzeit müssen vorhanden
sein. Die Images vorher gemäß [release.md](release.md) bauen. Unter Windows
`target/frontend/node/node.exe` statt `target/frontend/node/node` verwenden:

```sh
target/frontend/node/node deploy/load-smoke-test.mjs knowledgeroot:verified knowledgeroot:database-verified --pages=1000 --requests=160 --concurrency=1,8
```

Für einen größeren Bestand denselben Aufruf mit `--pages=10000` wiederholen.
Der Bericht `target/load-smoke.json` wird bei jedem Aufruf ersetzt; für Vergleiche
den jeweiligen Bericht vorher unter einem eigenen Namen sichern. Ungültige
Argumente entfernen ebenfalls einen vorherigen Erfolgsbericht.

| Option | Standard / zulässiger Bereich |
| --- | --- |
| `--pages` | 1000; gerade Zahl von 128 bis 20000, einschließlich Ordnerseiten |
| `--branches` | 32; gerade Zahl von 2 bis 64. Weniger Zweige bedeuten breitere Unterseitenlisten. |
| `--requests` | 160 je Parallelitätsstufe; Vielfaches von acht zwischen 80 und 8000 |
| `--concurrency` | `1,8`; bis zu vier unterschiedliche Stufen von 1 bis 16 |
| `--p95-ms` | Optional: zulässiges p95 je einzelnem Szenario, 1 bis 15000 ms |

Ein überschrittenes Zeitbudget oder eine falsche/fehlgeschlagene Antwort führt
zu Exitcode 1 und `passed: false`. Ohne Zeitbudget bedeutet Erfolg nur, dass
die Antworten korrekt waren. Er bedeutet keine akzeptable Produktionsleistung.
Auch Setup- oder Aufwärmfehler hinterlassen keinen Erfolgsbericht.

## Daten und Messung

Die zweistufige Hierarchie enthält gleich viele öffentliche und private Seiten
mit ungefähr 1,1 kB synthetischem Inhalt. Die Wurzeln vergeben Gast- beziehungsweise
Gruppenleserechte; Unterseiten erben diese. Pro gleichzeitigem Worker gibt es
eine eigene Gast-Sitzung und ein eigenes angemeldetes Leserkonto. Einrichtung,
Login und Aufwärmrequests liegen außerhalb der Messung; die Loginquoten werden
nicht verändert.

Acht gleich häufig ausgeführte Szenarien umfassen Gast-Suche, Folgeseite der
Suche, erfolglose Suche, öffentliche Seite, Unterseitenleiste, verweigerten
Privatseitenabruf sowie private Seite und Suche als Gruppenmitglied. Erwartete
403-Antworten sind erfolgreiche Zugriffsschutzprüfungen. Unerwartete Statuscodes,
fehlende erwartete Inhalte oder private Inhalte in Gastantworten sind Fehler.

Die Clients senden den für die Navigation üblichen HTMX-Header. Gemessen wird
vom Start des Requests bis zum vollständigen Lesen des Antwortkörpers, maximal
15 Sekunden pro Request. Je Worker läuft genau ein Request; nach Abschluss
folgt sofort der nächste. Diese feste Parallelität hat keine Denkpausen und
ist kein Modell einer konstanten externen Ankunftsrate. Fehler gehen in die
Latenzverteilung ein und werden zusätzlich ausgewiesen.

Der JSON-Bericht enthält p50/p95/p99, Maximum, Fehlerquote und Statusverteilung
insgesamt und pro Szenario sowie den Gesamtdurchsatz. Er nennt Parameter,
Image-IDs und deren Revisionen, die Revision des Testskripts, Docker-CPU/RAM
und Container-Ressourcenstände nach jeder Messphase. Die Ressourcenstände sind
Momentaufnahmen und keine Spitzenwerte. Der Testskriptstand kann neuer sein als
die unveränderte Anwendung im geprüften Image; beide Revisionen bleiben sichtbar.

## Verwendung und Grenzen

Die CI führt 1000 Seiten mit Parallelität 1 und 4 sowie je 80 Requests aus und
archiviert den Bericht auch bei Fehlschlag. Es gibt bewusst keine feste
Zeitgrenze für geteilte CI-Runner. Die Messhelfer werden zusätzlich mit echtem
lokalem HTTP auf begrenzte Parallelität, verzögerte Antwortkörper, Inhaltsfehler
und Verbindungsfehler geprüft.

Für die Betriebsabnahme den Test auf einem dafür vorgesehenen System mit den
geplanten Ressourcen ausführen, eigene Zeitgrenzen vorher festlegen und mehrere
Läufe vergleichen. Für belastbare Perzentile die Anzahl der Requests erhöhen;
der Standard liefert lediglich 20 Messwerte pro Szenario und Parallelitätsstufe.
Die Docker-Engine-Ressourcen und konkurrierende Prozesse beeinflussen das Ergebnis.

Dies ist ein kurzer synthetischer Lesevergleich, kein Kapazitäts- oder
Verfügbarkeitsnachweis. Browserdarstellung, TLS/Proxy, lange Hierarchien,
Schreibkonflikte, Uploads, Anhänge, große Einzelinhalte, Loginlast, Dauertests,
DB-Pool-Verläufe und externe Alarmierung benötigen eigene Prüfungen. Das
[Betriebsprotokoll](operational-acceptance.md) bleibt dafür maßgeblich.
