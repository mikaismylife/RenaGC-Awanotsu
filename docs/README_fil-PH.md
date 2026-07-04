# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Isang espirituwal na pagpapatuloy ng Grasscutter <b>server emulator</b> para sa <i>isang partikular na rhythm game</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: lagi naming tinatanggap ang mga contributor sa proyekto.

## Kasalukuyang features

* Pag-login
* Free lives
* Master-data CDN
* Asset CDN
* Remote GC console
* Account creation
* Server-side state control

## Paano ito naiiba sa Grasscutter: hybrid server, hindi pure GC

Ginagamit muli ng RenaGC-Awanotsu ang *hugis* ng Grasscutter (console na may mga `@Command` handler, `CommandMap` scan, account creation na nakabatay sa master DB, GM Handbook generator), pero **hindi** ito fork ng Grasscutter at ganap na iba ang wire:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transport | KCP/UDP + dispatch HTTP server | **gRPC + Protobuf** sa ibabaw ng HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | totoong dispatch/region server | **wala** — dalawang host: gRPC + CDN |
| Master data | lokal na Excel/Lua resources | **encrypted na `*.bin` tables** na dina-download ng client mula sa CDN (Rijndael-256) |
| Game assets | client-local | **Unity Addressables** (nasa package + remote bundle CDN) |

## Mabilis na setup guide

### Requirements

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — i-extract sa repo root, pagkatapos ay ilagay sa `config.json` ang local-only na `masterdata.keyHex`

### Build & run

```powershell
.\gradlew.bat build   # nagko-compile + gumagawa ng 36 gRPC services mula sa recovered protos
.\gradlew.bat run     # sinisimulan ang server (binabasa ang config.json)
```

| Port | Gamit |
|------|------|
| `:20000` | gRPC **h2c** (plaintext HTTP/2) — local testing + `grpcurl` |
| `:443` | gRPC **TLS** (self-signed, para sa totoong on-device client) |
| `:5080` / `:8443` | Master-data CDN (HTTP / HTTPS) |
| `:5081` / `:8444` | Asset CDN (HTTP / HTTPS) |
| `:5090` | Remote GC console (HTTP) |

### Verification

```powershell
.\gradlew.bat flowTest          # dinaanan ang Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # nagde-decrypt ng master table (Rijndael-256/gzip/JSON) at tine-test ang shape nito
```

### Pagkonekta ng totoong client

> **⚠️ Pag-expose ng port — HINDI gumagana rito ang Cloudflare tunnels.** Hindi tulad ng standard GC (na kayang i-expose ng simpleng `cloudflared` quick tunnel), ang client transport ng RenaGC-Awanotsu ay end-to-end **HTTP/2**, at ikinakabit ng redirect ang channel sa isang partikular na `:authority` (at sa working setup, cleartext **h2c**). Hindi ito naipapasa nang tama ng Cloudflare quick tunnel (kumpirmado sa testing). Gumamit sa halip ng **direktang LAN `IP:port`** (hal. `adb reverse` / proxy redirect sa parehong `/24`) o **raw-TCP / full HTTP-2-passthrough tunnel**. Tingnan ang [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Gumawa ng `id → name` reference para sa bawat master table (items, songs, member/support cards, characters, bands, stamps, atbp.) kasama ang listahan ng console commands:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Nire-resolve ang mga pangalan sa pamamagitan ng `MasterText` table (may Japanese / English / Traditional-Chinese / Simplified-Chinese / Korean columns); sa CBT1, karamihan ng non-Japanese cells ay hindi pa translated, kaya nagfa-fallback ang mga pangalan sa orihinal na Japanese title. Tingnan ang [docs/Commands.md](Commands.md) para sa command reference.

## Troubleshooting

Ang karaniwang issues at connect/redirect details ay nasa [docs/Troubleshooting.md](Troubleshooting.md) at [docs/Running.md](Running.md).
