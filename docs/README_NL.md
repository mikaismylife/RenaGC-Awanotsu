# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Een spirituele voortzetting van de Grasscutter-<b>serveremulator</b> voor <i>een bepaalde rhythmgame</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: bijdragers aan het project zijn altijd welkom.

## Huidige functies

* Inloggen
* Free lives
* Master-data CDN
* Asset CDN
* Remote GC console
* Account aanmaken
* Server-side statusbeheer

## Verschil met Grasscutter: een hybride server, geen pure GC

RenaGC-Awanotsu hergebruikt de *vorm* van Grasscutter (een console met `@Command`-handlers, een `CommandMap`-scan, accountcreatie vanuit een master-DB, een GM Handbook-generator), maar het is **geen** Grasscutter-fork en de netwerklaag is volledig anders:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transport | KCP/UDP + een dispatch-HTTP-server | **gRPC + Protobuf** over HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | een echte dispatch-/regioserver | **geen** — twee hosts: gRPC + CDN |
| Master data | lokale Excel-/Lua-resources | **versleutelde `*.bin`-tabellen** die de client van een CDN downloadt (Rijndael-256) |
| Game-assets | client-lokaal | **Unity Addressables** (in-package + een CDN voor remote bundles) |

## Snelle setup

### Vereisten

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — pak uit in de repo-root en vul daarna de alleen-lokale `masterdata.keyHex` in `config.json` in

### Bouwen en draaien

```powershell
.\gradlew.bat build   # compileert + genereert de 36 gRPC-services uit de recovered protos
.\gradlew.bat run     # start de server (leest config.json)
```

| Poort | Doel |
|------|------|
| `:20000` | gRPC **h2c** (plaintext HTTP/2) — lokale tests + `grpcurl` |
| `:443` | gRPC **TLS** (self-signed, voor de echte client op een apparaat) |
| `:5080` / `:8443` | Master-data CDN (HTTP / HTTPS) |
| `:5081` / `:8444` | Asset CDN (HTTP / HTTPS) |
| `:5090` | Remote GC console (HTTP) |

### Verificatie

```powershell
.\gradlew.bat flowTest          # doorloopt Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # decrypt een mastertabel (Rijndael-256/gzip/JSON) en controleert de structuur
```

### De echte client verbinden

> **⚠️ De poort openzetten: Cloudflare tunnels werken hier NIET.** Anders dan bij standaard GC (waar een simpele `cloudflared` quick tunnel prima werkt), gebruikt RenaGC-Awanotsu end-to-end **HTTP/2** voor clienttransport, waarbij de redirect het kanaal vastlegt op een specifieke `:authority` (en in de werkende configuratie cleartext **h2c** gebruikt). Een Cloudflare quick tunnel transporteert dit niet correct (bevestigd door onze tests). Gebruik in plaats daarvan een **direct LAN `IP:poort`** (bijv. `adb reverse` / de proxyredirect op dezelfde `/24`) of een **raw-TCP / volledige HTTP-2-passthrough-tunnel**. Zie [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Genereer een `id → name`-referentie voor elke mastertabel (items, songs, member-/supportcards, personages, bands, stamps, enz.) plus de lijst met consolecommando's:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Namen worden opgelost via de `MasterText`-tabel (met kolommen voor Japans / Engels / Traditioneel Chinees / Vereenvoudigd Chinees / Koreaans); in CBT1 zijn de meeste niet-Japanse cellen onvertaald, waardoor namen terugvallen op de originele Japanse titel. Zie [docs/Commands.md](Commands.md) voor de commandoreferentie.

## Probleemoplossing

Veelvoorkomende problemen en details over verbinden/redirecten staan in [docs/Troubleshooting.md](Troubleshooting.md) en [docs/Running.md](Running.md).
