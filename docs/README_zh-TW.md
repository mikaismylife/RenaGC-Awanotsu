# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">一個基於 Grasscutter 精神延續的<b>伺服器模擬器</b>，用於<i>某個音樂遊戲</i>。</div>

[EN](../README.md) | [簡中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **注意：**我們始終歡迎專案貢獻者。

## 目前功能

* 登入
* 自由演出
* 主資料 CDN
* 素材 CDN
* 遠端 GC 控制台
* 帳號建立
* 伺服器端狀態控制

## 與 Grasscutter 的不同之處：混合式伺服器，而非純 GC

RenaGC-Awanotsu 沿用了 Grasscutter 的*形態*（使用 `@Command` 處理器的控制台、`CommandMap` 掃描、由主資料庫驅動的帳號建立、GM Handbook 產生器），但它**不是** Grasscutter 的分支，且通訊鏈路完全不同：

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| 傳輸層 | KCP/UDP + 一個 dispatch HTTP 伺服器 | **gRPC + Protobuf**，承載於 HTTP/2 之上（h2c `:20000`，TLS `:443`） |
| "Dispatch" | 真正的 dispatch / 區域伺服器 | **沒有**——兩個主機：gRPC + CDN |
| 主資料 | 本機 Excel/Lua 資源 | 用戶端從 CDN 下載的**加密 `*.bin` 資料表**（Rijndael-256） |
| 遊戲素材 | 用戶端本機 | **Unity Addressables**（包內 + 遠端素材包 CDN） |

## 快速安裝指南

### 環境需求

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — 解壓縮到倉庫根目錄，然後在 `config.json` 中填入僅供本機使用的 `masterdata.keyHex`

### 建置與執行

```powershell
.\gradlew.bat build   # 編譯，並從 recovered protos 產生 36 個 gRPC 服務
.\gradlew.bat run     # 啟動伺服器（讀取 config.json）
```

| 連接埠 | 用途 |
|------|------|
| `:20000` | gRPC **h2c**（明文 HTTP/2）——本機測試 + `grpcurl` |
| `:443` | gRPC **TLS**（自簽憑證，用於真實裝置端用戶端） |
| `:5080` / `:8443` | 主資料 CDN（HTTP / HTTPS） |
| `:5081` / `:8444` | 素材 CDN（HTTP / HTTPS） |
| `:5090` | 遠端 GC 控制台（HTTP） |

### 驗證

```powershell
.\gradlew.bat flowTest          # 走過 Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # 解密一張主資料表（Rijndael-256/gzip/JSON）並驗證其結構
```

### 連接真實用戶端

> **⚠️ 暴露連接埠——Cloudflare tunnels 在此無法正常使用。** 與標準 GC（普通的 `cloudflared` quick tunnel 即可正常暴露）不同，RenaGC-Awanotsu 的用戶端傳輸是端到端 **HTTP/2**，重定向會把通道綁定到特定的 `:authority`（且在可用配置中為明文 **h2c**）。Cloudflare quick tunnel 無法正確透傳（已經測試確認）。請改用**直連區域網路的 `IP:port`**（例如 `adb reverse` / 同一 `/24` 內的代理重定向），或使用**原始 TCP / 完整 HTTP-2 透傳隧道**。詳見 [docs/Running.md](Running.md#exposing-the-server)。

## GM Handbook

為每一張主資料表（物品、歌曲、成員/支援卡、角色、樂隊、貼圖等）產生 `id → name` 對照，並附上控制台命令列表：

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

名稱會透過 `MasterText` 表解析（該表包含日文 / 英文 / 繁體中文 / 簡體中文 / 韓文欄位）；在 CBT1 中，多數非日文欄位尚未翻譯，名稱會回退到原始日文標題。命令參考見 [docs/Commands.md](Commands.md)。

## 疑難排解

常見問題以及連接/重定向細節見 [docs/Troubleshooting.md](Troubleshooting.md) 和 [docs/Running.md](Running.md)。
