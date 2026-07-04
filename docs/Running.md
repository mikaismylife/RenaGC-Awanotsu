# Running RenaGC

## 1. Prepare resources and config

Extract the resource archive into the repository root. After extraction, the tree
should contain:

```text
config.json
resources/
  masterdata/
  masterdata-overrides/
  asset/
```

Open `config.json` and fill in the relevant config keys:

| Key | Purpose |
|---|---|
| `grpc.host`, `grpc.port` | Plain h2c gRPC listener. |
| `mongo` | MongoDB connection string. MongoDB is required by the main server. |
| `masterdata.dir` | Folder containing encrypted master `*.bin` tables and `MasterDataSystemVersion.txt`. |
| `masterdata.overrideDir` | Optional folder whose files shadow matching masterdata files. |
| `masterdata.keyHex` | Local decode key used for master-data verification and tools. |
| `asset.dir` | Folder containing external asset bundles. |
| `tls.*` | Optional self-signed TLS listener settings. TLS material is generated when missing. |
| `console.*` | Optional remote GC console settings. It is disabled by default. |

## 2. Start MongoDB

RenaGC-Awanotsu can't run if MongoDB is unreachable. Start MongoDB first,
then confirm the URI in `config.json` matches your local service.

Typical local URI:

```text
mongodb://localhost:27017/renagc
```

## 3. Build and run

```powershell
.\gradlew.bat build
.\gradlew.bat run
```

A healthy boot shows:

* MongoDB connected.
* `MasterDataStore ... available=true`.
* gRPC h2c listening on the configured `grpc.port`.
* Master-data CDN listening if `masterdata.serveHttp` is enabled and masterdata is available.
* Asset CDN listening if `asset.serveHttp` is enabled and `asset.dir` exists.
* Remote GC console only if `console.remoteEnabled` is set to `true`.

## 4. Optionally verify the server without a client

Run the flow test against the configured gRPC port:

```powershell
.\gradlew.bat flowTest --args="localhost 20000"
```

If your `config.json` uses another `grpc.port`, pass that port instead.

You can also check gRPC reflection with `grpcurl`:

```powershell
grpcurl -plaintext localhost:20000 list
```

And verify local master-data readability:

```powershell
.\gradlew.bat masterdataSmoke
```

## 5. Optional TLS

TLS is disabled by default. If you enable it, RenaGC-Awanotsu will load the configured
PKCS12 file from `tls.p12Path`, or generate a fresh self-signed keystore if the file
does not exist.

Do not commit generated files under `certs/`. They are local runtime material.

You can test TLS generation and ALPN locally without exposing any ports:

```powershell
.\gradlew.bat tlsSmoke
```

## 6. Optional remote console

The HTTP remote GC console is disabled by default. If you enable it:

* Keep `console.remoteHost` as `127.0.0.1` unless you explicitly need LAN access.
* Set a non-empty `console.remoteToken`.
* Treat it as an operator surface, not a public web UI.

Local stdin commands are still available when running interactively.

## Exposing the server

For LAN testing, prefer a direct `IP:port` route to the configured gRPC port. If you
need a tunnel, use one that preserves raw TCP / full HTTP/2 behavior. HTTP reverse
proxies and convenience web tunnels may not preserve the stream semantics required by
the client transport.

See [Troubleshooting](Troubleshooting.md) for common startup and connection failures.
