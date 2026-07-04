# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Une continuation spirituelle de l'<b>émulateur de serveur</b> Grasscutter pour <i>un certain jeu de rythme</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention** : les contributions au projet sont toujours les bienvenues.

## Fonctionnalités actuelles

* Connexion
* Lives libres
* CDN de master-data
* CDN d'assets
* Console GC distante
* Création de comptes
* Contrôle d'état côté serveur

## Différences avec Grasscutter — un serveur hybride, pas un GC pur

RenaGC-Awanotsu réutilise la *forme* de Grasscutter (une console avec des gestionnaires `@Command`, un scan `CommandMap`, une création de compte pilotée par la master DB, un générateur de GM Handbook), mais ce n'est **pas** un fork de Grasscutter et le protocole réseau est complètement différent :

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transport | KCP/UDP + un serveur HTTP de dispatch | **gRPC + Protobuf** sur HTTP/2 (h2c `:20000`, TLS `:443`) |
| « Dispatch » | un vrai serveur de dispatch/région | **aucun** — deux hôtes : gRPC + CDN |
| Master data | ressources Excel/Lua locales | **tables `*.bin` chiffrées** téléchargées par le client depuis un CDN (Rijndael-256) |
| Assets du jeu | locaux au client | **Unity Addressables** (dans le paquet + CDN de bundle distant) |

## Guide de configuration rapide

### Prérequis

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — extrayez dans la racine du dépôt, puis renseignez le `masterdata.keyHex` local uniquement dans `config.json`

### Build et exécution

```powershell
.\gradlew.bat build   # compile + génère les 36 services gRPC depuis les recovered protos
.\gradlew.bat run     # démarre le serveur (lit config.json)
```

| Port | Usage |
|------|-------|
| `:20000` | gRPC **h2c** (HTTP/2 en clair) — tests locaux + `grpcurl` |
| `:443` | gRPC **TLS** (auto-signé, pour le vrai client sur appareil) |
| `:5080` / `:8443` | CDN de master-data (HTTP / HTTPS) |
| `:5081` / `:8444` | CDN d'assets (HTTP / HTTPS) |
| `:5090` | Console GC distante (HTTP) |

### Vérification

```powershell
.\gradlew.bat flowTest          # parcourt Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # déchiffre une table master (Rijndael-256/gzip/JSON) et vérifie sa structure
```

### Connexion du vrai client

> **⚠️ Exposition du port — les tunnels Cloudflare ne fonctionnent PAS ici.** Contrairement aux GC standards (qu'un simple quick tunnel `cloudflared` expose correctement), le transport client de RenaGC-Awanotsu est en **HTTP/2** de bout en bout, avec une redirection qui fixe le canal sur une `:authority` précise (et, dans la configuration fonctionnelle, en **h2c** clair). Un quick tunnel Cloudflare ne transporte pas cela correctement (confirmé par nos tests). Utilisez plutôt une **adresse LAN directe `IP:port`** (par exemple `adb reverse` / la redirection du proxy sur le même `/24`) ou un **tunnel TCP brut / passthrough HTTP-2 complet**. Voir [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Générez une référence `id → name` pour chaque table master (objets, morceaux, cartes membre/support, personnages, groupes, stamps, etc.) ainsi que la liste des commandes de la console :

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Les noms sont résolus via la table `MasterText` (qui contient des colonnes japonais / anglais / chinois traditionnel / chinois simplifié / coréen) ; dans CBT1, la plupart des cellules non japonaises ne sont pas traduites, donc les noms retombent sur le titre japonais original. Voir [docs/Commands.md](Commands.md) pour la référence des commandes.

## Dépannage

Les problèmes courants et les détails de connexion/redirection se trouvent dans [docs/Troubleshooting.md](Troubleshooting.md) et [docs/Running.md](Running.md).
