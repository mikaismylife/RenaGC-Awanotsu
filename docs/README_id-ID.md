# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Kelanjutan spiritual dari <b>emulator server</b> Grasscutter untuk <i>sebuah game ritme tertentu</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: kami selalu menyambut kontributor untuk proyek ini.

## Fitur saat ini

* Login
* Free lives
* Master-data CDN
* Asset CDN
* Remote GC console
* Pembuatan akun
* Kontrol state sisi server

## Perbedaannya dari Grasscutter: server hibrida, bukan GC murni

RenaGC-Awanotsu memakai ulang *bentuk* Grasscutter (konsol dengan handler `@Command`, pemindaian `CommandMap`, pembuatan akun berbasis master DB, generator GM Handbook), tetapi ini **bukan** fork Grasscutter dan jalur komunikasinya benar-benar berbeda:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transport | KCP/UDP + server HTTP dispatch | **gRPC + Protobuf** di atas HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | server dispatch/region sungguhan | **tidak ada** — dua host: gRPC + CDN |
| Master data | resource Excel/Lua lokal | **tabel `*.bin` terenkripsi** yang diunduh client dari CDN (Rijndael-256) |
| Asset game | lokal di client | **Unity Addressables** (dalam paket + CDN bundle remote) |

## Panduan setup cepat

### Kebutuhan

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — ekstrak ke root repo, lalu isi `masterdata.keyHex` yang hanya untuk lokal di `config.json`

### Build & run

```powershell
.\gradlew.bat build   # mengompilasi + membuat 36 layanan gRPC dari recovered protos
.\gradlew.bat run     # menjalankan server (membaca config.json)
```

| Port | Tujuan |
|------|--------|
| `:20000` | gRPC **h2c** (HTTP/2 plaintext) — pengujian lokal + `grpcurl` |
| `:443` | gRPC **TLS** (self-signed, untuk client sungguhan di perangkat) |
| `:5080` / `:8443` | Master-data CDN (HTTP / HTTPS) |
| `:5081` / `:8444` | Asset CDN (HTTP / HTTPS) |
| `:5090` | Remote GC console (HTTP) |

### Verifikasi

```powershell
.\gradlew.bat flowTest          # menjalankan Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # mendekripsi satu tabel master (Rijndael-256/gzip/JSON) dan memeriksa bentuknya
```

### Menghubungkan client sungguhan

> **⚠️ Mengekspos port — Cloudflare tunnels TIDAK berfungsi di sini.** Berbeda dari GC standar (yang dapat diekspos dengan quick tunnel `cloudflared` biasa), transport client RenaGC-Awanotsu adalah **HTTP/2** end-to-end, dengan redirect yang mengikat channel ke `:authority` tertentu (dan pada konfigurasi yang berfungsi, memakai **h2c** plaintext). Quick tunnel Cloudflare tidak meneruskannya dengan benar (sudah dikonfirmasi melalui pengujian). Gunakan **LAN langsung `IP:port`** (mis. `adb reverse` / redirect proxy pada `/24` yang sama) atau **tunnel raw-TCP / passthrough HTTP-2 penuh**. Lihat [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Buat referensi `id → name` untuk setiap tabel master (item, lagu, kartu member/support, karakter, band, stamp, dll.) plus daftar perintah konsol:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Nama diselesaikan melalui tabel `MasterText` (yang memiliki kolom Jepang / Inggris / Tionghoa Tradisional / Tionghoa Sederhana / Korea); di CBT1, sebagian besar sel non-Jepang belum diterjemahkan, sehingga nama fallback ke judul Jepang asli. Lihat [docs/Commands.md](Commands.md) untuk referensi perintah.

## Pemecahan masalah

Masalah umum serta detail koneksi/redirect ada di [docs/Troubleshooting.md](Troubleshooting.md) dan [docs/Running.md](Running.md).
