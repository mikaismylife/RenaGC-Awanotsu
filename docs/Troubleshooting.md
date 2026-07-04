# Troubleshooting

Common issues while setting up or running RenaGC-Awanotsu.

## Server exits with a MongoDB connection error

MongoDB is required by the main server. Start MongoDB and make sure `mongo` in
`config.json` points at the running instance.

Typical local value:

```text
mongodb://localhost:27017/renagc
```

If MongoDB is running but connection still fails, check firewall rules, the port, and
whether another MongoDB instance is bound to a different interface.

## `MasterDataStore ... available=false`

`masterdata.dir` does not point at a usable master-data folder. It must contain
encrypted `*.bin` files and `MasterDataSystemVersion.txt`.

Check:

* The resource archive was extracted into the repository root.
* `config.json` uses `resources/masterdata` or another correct local path.
* `MasterDataSystemVersion.txt` exists in that folder.
* The files were not nested one directory too deep during extraction.

The server may still start far enough to answer some gRPC calls, but master-driven
player creation, master-data CDN serving, and verification tools will be incomplete.

## `masterdataSmoke` fails

Most failures come from one of these:

* `masterdata.dir` is wrong or incomplete.
* `masterdata.keyHex` is incorrect or still blank, find it yourself.
* The configured key is not 64 hex characters.
* A table file is missing or corrupted.

After fixing `config.json`, run:

```powershell
.\gradlew.bat masterdataSmoke
```

## Asset CDN starts but content is missing

Confirm `asset.dir` points at the extracted `resources/asset` folder. Missing asset
requests are logged as WARN lines by the asset CDN. If a requested file is absent,
re-extract the resource archive and check that the platform subfolders were preserved.

Expected layout:

```text
resources/
  asset/
    Android/
    iOS/
```

Not every local workflow needs every asset, but missing bundles can stop client-side
screens from loading correctly.

## `flowTest` cannot connect

Make sure the server is already running and use the same port as `grpc.port` in
`config.json`.

```powershell
.\gradlew.bat flowTest --args="localhost 20000"
```

Also check that another process is not already using the configured gRPC port.

## `grpcurl` cannot list services

Use plaintext mode against the h2c port:

```powershell
grpcurl -plaintext localhost:20000 list
```

If you changed `grpc.port`, use that port. If the command is missing, install
`grpcurl` or use the bundled Gradle verification tasks instead.

## TLS listener does not start

TLS is optional and disabled by default. If enabled:

* Make sure the configured TLS port is free.
* Make sure `tls.password` matches any existing PKCS12 file at `tls.p12Path`.
* Delete the local generated keystore if you want RenaGC-Awanotsu to regenerate it.

Generated certs are local runtime files and should not be committed.

## Remote GC console is unreachable

The remote console is disabled by default. To use it, set:

```json
"console": {
  "remoteEnabled": true,
  "remotePort": 5090,
  "remoteHost": "127.0.0.1",
  "remoteToken": "change-me"
}
```

Use a real token before exposing it beyond localhost. If binding to LAN, change
`remoteHost` intentionally and check firewall rules.

## Exposing the server over the internet fails

RenaGC-Awanotsu uses HTTP/2 for the client transport. Some web-oriented tunnels and
reverse proxies do not preserve the required stream behavior. Prefer direct LAN
testing first. For remote access, use a raw TCP / full HTTP-2 passthrough tunnel.

See [Running](Running.md#exposing-the-server).

## `generateHandbook` shows raw keys such as `Item_Name_30`

The handbook generator resolves names through `MasterText`, then falls back where
possible. A raw key usually means the matching text row is absent or untranslated in
the source data.

Run it again after confirming `masterdata.dir` and `masterdata.keyHex` are correct:

```powershell
.\gradlew.bat generateHandbook
```
