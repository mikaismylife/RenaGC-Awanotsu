# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Una continuación espiritual del <b>emulador de servidor</b> Grasscutter para <i>cierto juego de ritmo</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: siempre damos la bienvenida a quienes contribuyen al proyecto.

## Funciones actuales

* Inicio de sesión
* Lives libres
* CDN de master-data
* CDN de assets
* Consola GC remota
* Creación de cuentas
* Control de estado del lado del servidor

## En qué se diferencia de Grasscutter: un servidor híbrido, no un GC puro

RenaGC-Awanotsu reutiliza la *forma* de Grasscutter (una consola con handlers `@Command`, escaneo `CommandMap`, creación de cuentas guiada por una master DB, un generador de GM Handbook), pero **no** es un fork de Grasscutter y el cableado de red es completamente diferente:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transporte | KCP/UDP + un servidor HTTP de dispatch | **gRPC + Protobuf** sobre HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | un servidor real de dispatch/región | **ninguno** — dos hosts: gRPC + CDN |
| Master data | recursos locales Excel/Lua | **tablas `*.bin` cifradas** que el cliente descarga desde un CDN (Rijndael-256) |
| Assets del juego | locales del cliente | **Unity Addressables** (en el paquete + un CDN de bundles remotos) |

## Guía rápida de configuración

### Requisitos

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — extrae en la raíz del repositorio y luego rellena el `masterdata.keyHex` solo local en `config.json`

### Compilar y ejecutar

```powershell
.\gradlew.bat build   # compila + genera los 36 servicios gRPC desde los recovered protos
.\gradlew.bat run     # inicia el servidor (lee config.json)
```

| Puerto | Propósito |
|------|-----------|
| `:20000` | gRPC **h2c** (HTTP/2 en texto claro) — pruebas locales + `grpcurl` |
| `:443` | gRPC **TLS** (autofirmado, para el cliente real en dispositivo) |
| `:5080` / `:8443` | CDN de master-data (HTTP / HTTPS) |
| `:5081` / `:8444` | CDN de assets (HTTP / HTTPS) |
| `:5090` | Consola GC remota (HTTP) |

### Verificación

```powershell
.\gradlew.bat flowTest          # recorre Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # descifra una tabla master (Rijndael-256/gzip/JSON) y comprueba su forma
```

### Conectar el cliente real

> **⚠️ Exponer el puerto: los túneles de Cloudflare NO funcionan aquí.** A diferencia de un GC estándar (que un quick tunnel simple de `cloudflared` expone sin problema), el transporte del cliente de RenaGC-Awanotsu es **HTTP/2** de extremo a extremo, con la redirección fijando el canal a una `:authority` concreta (y, en la configuración funcional, **h2c** en claro). Un quick tunnel de Cloudflare no lo transporta correctamente (confirmado en nuestras pruebas). Usa en su lugar una **`IP:puerto` directa en LAN** (por ejemplo `adb reverse` / la redirección del proxy en el mismo `/24`) o un **túnel TCP bruto / passthrough HTTP-2 completo**. Consulta [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Genera una referencia `id → name` para cada tabla master (objetos, canciones, cartas de miembro/soporte, personajes, bandas, stamps, etc.) junto con la lista de comandos de consola:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Los nombres se resuelven mediante la tabla `MasterText` (que contiene columnas de japonés / inglés / chino tradicional / chino simplificado / coreano); en CBT1, la mayoría de las celdas no japonesas siguen sin traducir, así que los nombres vuelven al título japonés original. Consulta [docs/Commands.md](Commands.md) para la referencia de comandos.

## Solución de problemas

Los problemas comunes y los detalles de conexión/redirección están en [docs/Troubleshooting.md](Troubleshooting.md) y [docs/Running.md](Running.md).
