# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center"><i>あるリズムゲーム</i>向けの、Grasscutter の精神を受け継ぐ<b>サーバーエミュレーター</b>です。</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: このプロジェクトへのコントリビューションはいつでも歓迎しています。

## 現在の機能

* ログイン
* フリーライブ
* Master-data CDN
* Asset CDN
* Remote GC console
* アカウント作成
* サーバー側の状態制御

## Grasscutter との違い：純粋な GC ではなく、ハイブリッドサーバー

RenaGC-Awanotsu は Grasscutter の*形*（`@Command` ハンドラーを持つコンソール、`CommandMap` スキャン、マスター DB に基づくアカウント作成、GM Handbook ジェネレーター）を再利用していますが、**Grasscutter のフォークではありません**。通信方式も完全に異なります。

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| トランスポート | KCP/UDP + dispatch HTTP サーバー | HTTP/2 上の **gRPC + Protobuf**（h2c `:20000`、TLS `:443`） |
| "Dispatch" | 実際の dispatch / region サーバー | **なし**——gRPC + CDN の 2 つのホスト |
| マスターデータ | ローカルの Excel/Lua リソース | クライアントが CDN から取得する**暗号化 `*.bin` テーブル**（Rijndael-256） |
| ゲームアセット | クライアントローカル | **Unity Addressables**（パッケージ内 + リモートバンドル CDN） |

## クイックセットアップ

### 必要なもの

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — リポジトリのルートへ展開し、その後 `config.json` のローカル専用 `masterdata.keyHex` を入力してください

### ビルドと実行

```powershell
.\gradlew.bat build   # recovered protos から 36 個の gRPC サービスを生成してコンパイル
.\gradlew.bat run     # サーバーを起動（config.json を読み込み）
```

| ポート | 用途 |
|------|------|
| `:20000` | gRPC **h2c**（平文 HTTP/2）——ローカルテスト + `grpcurl` |
| `:443` | gRPC **TLS**（自己署名、実機クライアント向け） |
| `:5080` / `:8443` | Master-data CDN（HTTP / HTTPS） |
| `:5081` / `:8444` | Asset CDN（HTTP / HTTPS） |
| `:5090` | Remote GC console（HTTP） |

### 検証

```powershell
.\gradlew.bat flowTest          # Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking を実行
.\gradlew.bat masterdataSmoke   # マスターテーブルを復号（Rijndael-256/gzip/JSON）し、構造を検証
```

### 実クライアントの接続

> **⚠️ ポート公開について：Cloudflare tunnels はここでは動作しません。** 通常の GC（単純な `cloudflared` quick tunnel で問題なく公開できるもの）とは異なり、RenaGC-Awanotsu のクライアント通信はエンドツーエンドの **HTTP/2** で、リダイレクトによりチャンネルが特定の `:authority` に固定されます（動作確認済み構成では平文 **h2c**）。Cloudflare quick tunnel はこれを正しく通せません（テストで確認済み）。代わりに、**LAN 上の直接 `IP:port`**（例：`adb reverse` / 同一 `/24` 内のプロキシリダイレクト）または **raw TCP / 完全な HTTP-2 パススルートンネル**を使ってください。詳しくは [docs/Running.md](Running.md#exposing-the-server) を参照してください。

## GM Handbook

各マスターテーブル（アイテム、楽曲、メンバー/サポートカード、キャラクター、バンド、スタンプなど）について `id → name` の参照表を生成し、コンソールコマンド一覧も出力します。

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

名前は `MasterText` テーブルから解決されます（日本語 / 英語 / 繁体字中国語 / 簡体字中国語 / 韓国語の列を含みます）。CBT1 では非日本語セルの多くが未翻訳のため、名前は元の日本語タイトルへフォールバックします。コマンドリファレンスは [docs/Commands.md](Commands.md) を参照してください。

## トラブルシューティング

よくある問題と接続/リダイレクトの詳細は [docs/Troubleshooting.md](Troubleshooting.md) と [docs/Running.md](Running.md) にあります。
