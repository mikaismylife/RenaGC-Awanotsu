# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">一个基于 Grasscutter 的<b>服务器模拟器</b>，用于<i>某个音游</i>。</div>

[EN](README.md) | [简中](docs/README_zh-CN.md) | [繁中](docs/README_zh-TW.md) | [日本語](docs/README_ja-JP.md) | [한국어](docs/README_ko-KR.md) | [FR](docs/README_fr-FR.md) | [ES](docs/README_es-ES.md) | [RU](docs/README_ru-RU.md) | [PL](docs/README_pl-PL.md) | [ID](docs/README_id-ID.md) | [IT](docs/README_it-IT.md) | [VI](docs/README_vi-VN.md) | [NL](docs/README_NL.md) | [HE](docs/README_HE.md) | [FIL/PH](docs/README_fil-PH.md)

> **注意：**我们始终欢迎项目的贡献者。

## 当前功能

* 登录
* 自由演出
* 主数据 CDN
* 素材 CDN
* 远程 GC 控制台
* 账号创建
* 服务端状态控制

## 与普通 Grasscutter 不同的是：此服务器模拟采用混合的模式，而非纯粹的 GC

RenaGC-Awanotsu 沿用了 Grasscutter 的*形态*（使用 `@Command` 处理器的控制台、`CommandMap` 扫描、由主数据库驱动的账号创建、GM Handbook 生成器），但其**并不属于** Grasscutter 的分支，且通信链路完全不同：

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| 传输层 | KCP/UDP + 一个 dispatch HTTP 服务器 | **gRPC + Protobuf**，承载于 HTTP/2 之上（h2c `:20000`，TLS `:443`） |
| "Dispatch" | 一个真正的 dispatch / 区域服务器 | **无**——两个主机：gRPC + CDN |
| 主数据 | 本地的 Excel/Lua 资源 | 客户端从 CDN 下载的**加密 `*.bin` 数据表**（Rijndael-256） |
| 游戏素材 | 客户端本地 | **Unity Addressables**（包内 + 一个远程资源包 CDN） |

## 快速安装指南

### 环境要求

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — 解压到仓库根目录，然后在 `config.json` 中填写仅限本地使用的 `masterdata.keyHex`

### 构建及运行

```powershell
.\gradlew.bat build   # compiles + generates the 36 gRPC services from the recovered protos
.\gradlew.bat run     # starts the server (reads config.json)
```

| 端口 | 用途 |
|------|------|
| `:20000` | gRPC **h2c**（明文 HTTP/2）——本地测试 + `grpcurl` |
| `:443` | gRPC **TLS**（自签名，用于真实的设备端客户端） |
| `:5080` / `:8443` | 主数据 CDN（HTTP / HTTPS） |
| `:5081` / `:8444` | 素材 CDN（HTTP / HTTPS） |
| `:5090` | 远程 GC 控制台（HTTP） |

### 验证

```powershell
.\gradlew.bat flowTest          # walks Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # decrypts a master table (Rijndael-256/gzip/JSON) and asserts its shape
```

### 连接真实客户端

> **⚠️ Cloudflared Tunnel 在此无法正常使用** 与普通的 Grasscutter 服务器模拟器不同（普通的 `cloudflared` 快速隧道即可正常暴露），RenaGC-Awanotsu 的客户端传输是端到端的 **HTTP/2**，且重定向会把通道绑定到某个特定的 `:authority`（并且在可用的配置中是明文 **h2c**）。Cloudflare 快速隧道无法正常透传（已经测试确认）。需改用**直连局域网的 `IP:port`**（例如 `adb reverse` / 同一 `/24` 网段内的代理重定向），或一条**原始 TCP / 完整 HTTP-2 透传隧道**。详见 [docs/Running.md](docs/Running.md#exposing-the-server)。

## GM Handbook

为每一张主数据表（物品、歌曲、卡面、角色、乐队、表情 等）生成一份 `id → name` 对照，外加控制台命令列表：

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

名称通过 `MasterText` 表解析（该表带有日文 / 英文 / 繁体中文 / 简体中文 / 韩文等列）；由于 CBT1 数据中大多数非日文单元格仍未翻译，名称会回退到真正的日文标题。命令参考见 [docs/Commands.md](docs/Commands.md)。

## 疑难排查

常见问题以及连接/重定向细节见 [docs/Troubleshooting.md](docs/Troubleshooting.md) 和 [docs/Running.md](docs/Running.md)。
