# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">A spiritual continuation of Grasscutter <b>server emulator</b> for <i>a certain rhythm game</i>.</div>

[EN](README.md) | [简中](docs/README_zh-CN.md) | [繁中](docs/README_zh-TW.md) | [日本語](docs/README_ja-JP.md) | [한국어](docs/README_ko-KR.md) | [FR](docs/README_fr-FR.md) | [ES](docs/README_es-ES.md) | [RU](docs/README_ru-RU.md) | [PL](docs/README_pl-PL.md) | [ID](docs/README_id-ID.md) | [IT](docs/README_it-IT.md) | [VI](docs/README_vi-VN.md) | [NL](docs/README_NL.md) | [HE](docs/README_HE.md) | [FIL/PH](docs/README_fil-PH.md)

> **Attention**: We always welcome contributors to the project.

## Current features

* Logging in
* Free lives
* Master-data CDN
* Asset CDN
* Remote GC console
* Account creation
* Server-side state control

## How this differs from Grasscutter — *a hybrid server, not a pure GC*

RenaGC-Awanotsu reuses Grasscutter's *shape* (a console with `@Command` handlers, a `CommandMap` scan, a master-DB-driven account creation, a GM-handbook generator) but it is **not** a Grasscutter fork and the wire is completely different:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transport | KCP/UDP + a dispatch HTTP server | **gRPC + Protobuf** over HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | a real dispatch/region server | **none** — two hosts: gRPC + CDN |
| Master data | local Excel/Lua resources | **encrypted `*.bin` tables** the client downloads from a CDN (Rijndael-256) |
| Game assets | client-local | **Unity Addressables** (in-package + a remote bundle CDN) |

## Quick setup guide

### Requirements

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — extract into the repo root, then fill the local-only `masterdata.keyHex` in `config.json`

### Build & run

```powershell
.\gradlew.bat build   # compiles + generates the 36 gRPC services from the recovered protos
.\gradlew.bat run     # starts the server (reads config.json)
```

| Port | Purpose |
|------|---------|
| `:20000` | gRPC **h2c** (plaintext HTTP/2) — local testing + `grpcurl` |
| `:443` | gRPC **TLS** (self-signed, for the real on-device client) |
| `:5080` / `:8443` | Master-data CDN (HTTP / HTTPS) |
| `:5081` / `:8444` | Asset CDN (HTTP / HTTPS) |
| `:5090` | Remote GC console (HTTP) |

### Verification

```powershell
.\gradlew.bat flowTest          # walks Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # decrypts a master table (Rijndael-256/gzip/JSON) and asserts its shape
```

### Connecting the real client

> **⚠️ Exposing the port — Cloudflare tunnels do NOT work here.** Unlike standard GC (which a plain `cloudflared` quick tunnel exposes fine), RenaGC-Awanotsu's client transport is end-to-end **HTTP/2** with the redirect committing the channel to a specific `:authority` (and, in the working setup, cleartext **h2c**). A Cloudflare quick tunnel does not carry that through (confirmed by our testing). Use a **direct LAN `IP:port`** (e.g. `adb reverse` / the proxy redirect on the same `/24`) or a **raw-TCP / full HTTP-2-passthrough tunnel** instead. See [docs/Running.md](docs/Running.md#exposing-the-server).

## GM Handbook

Generate an `id → name` reference for every master table (items, songs, member/support cards, characters, bands, stamps, …) plus the console command list:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Names resolve through the `MasterText` table (which carries Japanese / English / Traditional-Chinese / Simplified-Chinese / Korean columns); In CBT1, the data leaves most non-Japanese cells untranslated, names fall back to the original Japanese title. See [docs/Commands.md](docs/Commands.md) for the command reference.

## Troubleshooting

Common issues and the connect/redirect details are in [docs/Troubleshooting.md](docs/Troubleshooting.md) and [docs/Running.md](docs/Running.md).
