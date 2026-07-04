# Building from source

RenaGC-Awanotsu uses Gradle (wrapper included). Output is a normal gRPC + HTTP Java application.

## Requirements

* **[JDK 21](https://adoptium.net/)** — the Gradle toolchain pins Java 21 (`languageVersion = 21`).
* **[Git](https://git-scm.com/downloads)**.
* **[MongoDB](https://www.mongodb.com/try/download/community)**

## Compile

```powershell
.\gradlew.bat build
```

## Run / verify

```powershell
.\gradlew.bat run               # start the server
.\gradlew.bat flowTest          # login → home → play → result against a running server
.\gradlew.bat masterdataSmoke   # decrypt a master table (no running server needed)
.\gradlew.bat generateHandbook  # write "GM Handbook - <LANG>.txt"
```

## Notes

* **Crypto:** master data is **Rijndael-256** (256-bit *block*) CBC/PKCS7 — the JCE cannot
  do this (AES is fixed at a 128-bit block), so the build pulls in **BouncyCastle**
  (`RijndaelEngine`). `bcpkix` is used to mint the self-signed TLS cert (with the
  `api-cbt` / `static-cbt` SANs) the real client needs.
