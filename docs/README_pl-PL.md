# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Duchowa kontynuacja <b>emulatora serwera</b> Grasscutter dla <i>pewnej gry rytmicznej</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: zawsze chętnie przyjmujemy osoby chcące współtworzyć projekt.

## Obecne funkcje

* Logowanie
* Free lives
* CDN master-data
* CDN zasobów
* Zdalna konsola GC
* Tworzenie kont
* Kontrola stanu po stronie serwera

## Czym różni się od Grasscuttera: serwer hybrydowy, nie czysty GC

RenaGC-Awanotsu wykorzystuje *kształt* Grasscuttera (konsolę z handlerami `@Command`, skan `CommandMap`, tworzenie kont oparte na master-DB, generator GM Handbook), ale **nie** jest forkiem Grasscuttera, a warstwa komunikacji jest całkowicie inna:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transport | KCP/UDP + serwer HTTP dispatch | **gRPC + Protobuf** przez HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | prawdziwy serwer dispatch/region | **brak** — dwa hosty: gRPC + CDN |
| Master data | lokalne zasoby Excel/Lua | **zaszyfrowane tabele `*.bin`** pobierane przez klienta z CDN (Rijndael-256) |
| Zasoby gry | lokalne po stronie klienta | **Unity Addressables** (w pakiecie + zdalny CDN bundle'i) |

## Szybka konfiguracja

### Wymagania

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — rozpakuj do katalogu głównego repozytorium, a następnie uzupełnij lokalny `masterdata.keyHex` w `config.json`

### Budowanie i uruchamianie

```powershell
.\gradlew.bat build   # kompiluje + generuje 36 usług gRPC z recovered protos
.\gradlew.bat run     # uruchamia serwer (czyta config.json)
```

| Port | Cel |
|------|-----|
| `:20000` | gRPC **h2c** (jawny HTTP/2) — testy lokalne + `grpcurl` |
| `:443` | gRPC **TLS** (samopodpisany, dla prawdziwego klienta na urządzeniu) |
| `:5080` / `:8443` | CDN master-data (HTTP / HTTPS) |
| `:5081` / `:8444` | CDN zasobów (HTTP / HTTPS) |
| `:5090` | Zdalna konsola GC (HTTP) |

### Weryfikacja

```powershell
.\gradlew.bat flowTest          # przechodzi Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # odszyfrowuje tabelę master (Rijndael-256/gzip/JSON) i sprawdza jej strukturę
```

### Łączenie prawdziwego klienta

> **⚠️ Wystawianie portu — tunele Cloudflare tutaj NIE działają.** W przeciwieństwie do standardowego GC (które zwykły quick tunnel `cloudflared` wystawia bez problemu), transport klienta RenaGC-Awanotsu to end-to-end **HTTP/2**, a przekierowanie wiąże kanał z konkretnym `:authority` (w działającej konfiguracji jest to jawne **h2c**). Quick tunnel Cloudflare nie przenosi tego poprawnie (potwierdzone testami). Zamiast tego użyj **bezpośredniego LAN `IP:port`** (np. `adb reverse` / przekierowania proxy w tej samej sieci `/24`) albo **surowego TCP / pełnego tunelu passthrough HTTP-2**. Zobacz [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Wygeneruj referencję `id → name` dla każdej tabeli master (przedmioty, piosenki, karty member/support, postacie, zespoły, stampy itd.) oraz listę komend konsoli:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Nazwy są rozwiązywane przez tabelę `MasterText` (z kolumnami japońską / angielską / tradycyjnego chińskiego / uproszczonego chińskiego / koreańską); w CBT1 większość nie-japońskich komórek jest nieprzetłumaczona, więc nazwy wracają do oryginalnego tytułu japońskiego. Referencja komend znajduje się w [docs/Commands.md](Commands.md).

## Rozwiązywanie problemów

Typowe problemy oraz szczegóły połączenia/przekierowania znajdują się w [docs/Troubleshooting.md](Troubleshooting.md) i [docs/Running.md](Running.md).
