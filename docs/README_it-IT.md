# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Una continuazione spirituale dell'<b>emulatore server</b> Grasscutter per <i>un certo rhythm game</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: accogliamo sempre con piacere chi contribuisce al progetto.

## Funzionalità attuali

* Login
* Live libere
* CDN dei master data
* CDN degli asset
* Console GC remota
* Creazione account
* Controllo dello stato lato server

## Differenze da Grasscutter: un server ibrido, non un GC puro

RenaGC-Awanotsu riusa la *forma* di Grasscutter (una console con handler `@Command`, scansione `CommandMap`, creazione account guidata dal master DB, generatore di GM Handbook), ma **non** è un fork di Grasscutter e il protocollo di rete è completamente diverso:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Trasporto | KCP/UDP + server HTTP di dispatch | **gRPC + Protobuf** su HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | un vero server dispatch/region | **nessuno** — due host: gRPC + CDN |
| Master data | risorse locali Excel/Lua | **tabelle `*.bin` cifrate** scaricate dal client da un CDN (Rijndael-256) |
| Asset di gioco | locali al client | **Unity Addressables** (nel pacchetto + CDN di bundle remoti) |

## Guida rapida alla configurazione

### Requisiti

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — estrai nella radice del repository, poi compila in `config.json` il `masterdata.keyHex` solo locale

### Build ed esecuzione

```powershell
.\gradlew.bat build   # compila + genera i 36 servizi gRPC dai recovered protos
.\gradlew.bat run     # avvia il server (legge config.json)
```

| Porta | Scopo |
|------|-------|
| `:20000` | gRPC **h2c** (HTTP/2 in chiaro) — test locali + `grpcurl` |
| `:443` | gRPC **TLS** (self-signed, per il client reale su dispositivo) |
| `:5080` / `:8443` | CDN dei master data (HTTP / HTTPS) |
| `:5081` / `:8444` | CDN degli asset (HTTP / HTTPS) |
| `:5090` | Console GC remota (HTTP) |

### Verifica

```powershell
.\gradlew.bat flowTest          # percorre Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # decifra una tabella master (Rijndael-256/gzip/JSON) e ne verifica la struttura
```

### Collegare il client reale

> **⚠️ Esporre la porta: i tunnel Cloudflare NON funzionano qui.** A differenza di un GC standard (che un semplice quick tunnel `cloudflared` espone senza problemi), il trasporto del client di RenaGC-Awanotsu è **HTTP/2** end-to-end, con il redirect che vincola il canale a una specifica `:authority` (e, nella configurazione funzionante, **h2c** in chiaro). Un quick tunnel Cloudflare non lo trasporta correttamente (confermato dai nostri test). Usa invece un **`IP:porta` diretto in LAN** (per esempio `adb reverse` / il redirect del proxy sullo stesso `/24`) oppure un **tunnel raw-TCP / passthrough HTTP-2 completo**. Vedi [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Genera un riferimento `id → name` per ogni tabella master (oggetti, brani, carte membro/supporto, personaggi, band, stamp, ecc.) più l'elenco dei comandi della console:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

I nomi vengono risolti tramite la tabella `MasterText` (che contiene colonne giapponese / inglese / cinese tradizionale / cinese semplificato / coreano); in CBT1, la maggior parte delle celle non giapponesi non è tradotta, quindi i nomi ricadono sul titolo giapponese originale. Consulta [docs/Commands.md](Commands.md) per il riferimento dei comandi.

## Risoluzione dei problemi

Problemi comuni e dettagli di connessione/reindirizzamento si trovano in [docs/Troubleshooting.md](Troubleshooting.md) e [docs/Running.md](Running.md).
