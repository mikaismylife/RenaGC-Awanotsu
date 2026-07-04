# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center"><i>어떤 리듬 게임</i>을 위한, Grasscutter의 정신을 잇는 <b>서버 에뮬레이터</b>입니다.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: 프로젝트 기여자는 언제나 환영합니다.

## 현재 기능

* 로그인
* 프리 라이브
* Master-data CDN
* Asset CDN
* Remote GC console
* 계정 생성
* 서버 측 상태 제어

## Grasscutter와의 차이점: 순수 GC가 아닌 하이브리드 서버

RenaGC-Awanotsu는 Grasscutter의 *형태*(`@Command` 핸들러가 있는 콘솔, `CommandMap` 스캔, 마스터 DB 기반 계정 생성, GM Handbook 생성기)를 재사용하지만, **Grasscutter의 포크가 아니며** 통신 방식도 완전히 다릅니다.

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| 전송 | KCP/UDP + dispatch HTTP 서버 | HTTP/2 위의 **gRPC + Protobuf** (h2c `:20000`, TLS `:443`) |
| "Dispatch" | 실제 dispatch / region 서버 | **없음** — 두 호스트: gRPC + CDN |
| 마스터 데이터 | 로컬 Excel/Lua 리소스 | 클라이언트가 CDN에서 다운로드하는 **암호화된 `*.bin` 테이블** (Rijndael-256) |
| 게임 에셋 | 클라이언트 로컬 | **Unity Addressables** (패키지 내 + 원격 번들 CDN) |

## 빠른 설정 가이드

### 요구 사항

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — 저장소 루트에 압축을 푼 뒤, `config.json`의 로컬 전용 `masterdata.keyHex`를 채우세요

### 빌드 및 실행

```powershell
.\gradlew.bat build   # recovered protos에서 36개의 gRPC 서비스를 생성하고 컴파일합니다
.\gradlew.bat run     # 서버를 시작합니다(config.json 읽기)
```

| 포트 | 용도 |
|------|------|
| `:20000` | gRPC **h2c** (평문 HTTP/2) — 로컬 테스트 + `grpcurl` |
| `:443` | gRPC **TLS** (자체 서명, 실제 기기 클라이언트용) |
| `:5080` / `:8443` | Master-data CDN (HTTP / HTTPS) |
| `:5081` / `:8444` | Asset CDN (HTTP / HTTPS) |
| `:5090` | Remote GC console (HTTP) |

### 검증

```powershell
.\gradlew.bat flowTest          # Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking 흐름 실행
.\gradlew.bat masterdataSmoke   # 마스터 테이블을 복호화(Rijndael-256/gzip/JSON)하고 구조를 검증합니다
```

### 실제 클라이언트 연결

> **⚠️ 포트 노출 — Cloudflare tunnels는 여기서 작동하지 않습니다.** 일반적인 GC(단순한 `cloudflared` quick tunnel로 잘 노출되는 경우)와 달리, RenaGC-Awanotsu의 클라이언트 전송은 엔드투엔드 **HTTP/2**이며 리다이렉트가 채널을 특정 `:authority`에 고정합니다(동작하는 설정에서는 평문 **h2c**). Cloudflare quick tunnel은 이를 제대로 전달하지 못합니다(테스트로 확인됨). 대신 **LAN의 직접 `IP:port`**(예: `adb reverse` / 같은 `/24`의 프록시 리다이렉트) 또는 **raw-TCP / 완전한 HTTP-2 passthrough 터널**을 사용하세요. 자세한 내용은 [docs/Running.md](Running.md#exposing-the-server)를 참고하세요.

## GM Handbook

모든 마스터 테이블(아이템, 곡, 멤버/서포트 카드, 캐릭터, 밴드, 스탬프 등)에 대한 `id → name` 참조와 콘솔 명령 목록을 생성합니다.

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

이름은 `MasterText` 테이블을 통해 해석됩니다(일본어 / 영어 / 번체 중국어 / 간체 중국어 / 한국어 열 포함). CBT1에서는 대부분의 비일본어 셀이 번역되지 않아 이름이 원래 일본어 제목으로 폴백됩니다. 명령 참조는 [docs/Commands.md](Commands.md)를 참고하세요.

## 문제 해결

일반적인 문제와 연결/리다이렉트 세부 사항은 [docs/Troubleshooting.md](Troubleshooting.md) 및 [docs/Running.md](Running.md)에 있습니다.
