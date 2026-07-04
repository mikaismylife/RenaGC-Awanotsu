# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Духовное продолжение <b>эмулятора сервера</b> Grasscutter для <i>одной ритм-игры</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: мы всегда рады участникам, готовым помогать проекту.

## Текущие возможности

* Вход в аккаунт
* Free lives
* CDN master-data
* CDN ассетов
* Удалённая GC-консоль
* Создание аккаунтов
* Управление состоянием на стороне сервера

## Чем отличается от Grasscutter: гибридный сервер, а не чистый GC

RenaGC-Awanotsu использует *форму* Grasscutter (консоль с обработчиками `@Command`, сканирование `CommandMap`, создание аккаунта на основе master-DB, генератор GM Handbook), но **не является** форком Grasscutter, а сетевой протокол полностью другой:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Транспорт | KCP/UDP + HTTP-сервер dispatch | **gRPC + Protobuf** поверх HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | настоящий сервер dispatch/region | **нет** — два хоста: gRPC + CDN |
| Master data | локальные ресурсы Excel/Lua | **зашифрованные таблицы `*.bin`**, которые клиент скачивает с CDN (Rijndael-256) |
| Игровые ассеты | локально у клиента | **Unity Addressables** (в пакете + удалённый CDN bundle'ов) |

## Быстрый запуск

### Требования

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — распакуйте в корень репозитория, затем заполните локальный `masterdata.keyHex` в `config.json`

### Сборка и запуск

```powershell
.\gradlew.bat build   # компилирует и генерирует 36 gRPC-сервисов из recovered protos
.\gradlew.bat run     # запускает сервер (читает config.json)
```

| Порт | Назначение |
|------|------------|
| `:20000` | gRPC **h2c** (открытый HTTP/2) — локальные тесты + `grpcurl` |
| `:443` | gRPC **TLS** (самоподписанный, для настоящего клиента на устройстве) |
| `:5080` / `:8443` | CDN master-data (HTTP / HTTPS) |
| `:5081` / `:8444` | CDN ассетов (HTTP / HTTPS) |
| `:5090` | Удалённая GC-консоль (HTTP) |

### Проверка

```powershell
.\gradlew.bat flowTest          # проходит Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # расшифровывает master-таблицу (Rijndael-256/gzip/JSON) и проверяет её структуру
```

### Подключение настоящего клиента

> **⚠️ Открытие порта — туннели Cloudflare здесь НЕ работают.** В отличие от стандартного GC (который обычный quick tunnel `cloudflared` открывает без проблем), транспорт клиента RenaGC-Awanotsu — это сквозной **HTTP/2**, а редирект привязывает канал к конкретному `:authority` (в рабочей конфигурации используется открытый **h2c**). Cloudflare quick tunnel не передаёт это корректно (подтверждено нашими тестами). Используйте вместо этого **прямой LAN `IP:port`** (например, `adb reverse` / прокси-редирект в той же `/24`) или **raw-TCP / полноценный HTTP-2 passthrough-туннель**. См. [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Сгенерируйте справочник `id → name` для каждой master-таблицы (предметы, песни, member/support cards, персонажи, группы, stamps и т. д.) плюс список консольных команд:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Имена разрешаются через таблицу `MasterText` (в ней есть японская / английская / традиционная китайская / упрощённая китайская / корейская колонки); в CBT1 большинство не-японских ячеек не переведены, поэтому имена откатываются к исходному японскому названию. Справочник команд см. в [docs/Commands.md](Commands.md).

## Устранение неполадок

Типичные проблемы и детали подключения/редиректа находятся в [docs/Troubleshooting.md](Troubleshooting.md) и [docs/Running.md](Running.md).
