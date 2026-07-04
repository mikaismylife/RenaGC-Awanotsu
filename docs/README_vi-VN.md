# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">Một sự tiếp nối tinh thần của <b>trình giả lập máy chủ</b> Grasscutter cho <i>một trò chơi nhịp điệu nào đó</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: chúng tôi luôn chào đón những người đóng góp cho dự án.

## Tính năng hiện tại

* Đăng nhập
* Free lives
* Master-data CDN
* Asset CDN
* Remote GC console
* Tạo tài khoản
* Điều khiển trạng thái phía máy chủ

## Khác gì so với Grasscutter: một máy chủ lai, không phải GC thuần

RenaGC-Awanotsu tái sử dụng *hình dạng* của Grasscutter (console với các handler `@Command`, quét `CommandMap`, tạo tài khoản dựa trên master DB, trình tạo GM Handbook), nhưng nó **không phải** là fork của Grasscutter và đường truyền hoàn toàn khác:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transport | KCP/UDP + một dispatch HTTP server | **gRPC + Protobuf** trên HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | một dispatch/region server thật | **không có** — hai host: gRPC + CDN |
| Master data | tài nguyên Excel/Lua cục bộ | **các bảng `*.bin` được mã hóa** mà client tải từ CDN (Rijndael-256) |
| Asset game | cục bộ trong client | **Unity Addressables** (trong gói + CDN bundle từ xa) |

## Hướng dẫn thiết lập nhanh

### Yêu cầu

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — giải nén vào thư mục gốc của repo, rồi điền `masterdata.keyHex` chỉ dùng cục bộ trong `config.json`

### Build & run

```powershell
.\gradlew.bat build   # biên dịch + tạo 36 dịch vụ gRPC từ recovered protos
.\gradlew.bat run     # khởi động máy chủ (đọc config.json)
```

| Cổng | Mục đích |
|------|----------|
| `:20000` | gRPC **h2c** (HTTP/2 plaintext) — kiểm thử cục bộ + `grpcurl` |
| `:443` | gRPC **TLS** (tự ký, cho client thật trên thiết bị) |
| `:5080` / `:8443` | Master-data CDN (HTTP / HTTPS) |
| `:5081` / `:8444` | Asset CDN (HTTP / HTTPS) |
| `:5090` | Remote GC console (HTTP) |

### Xác minh

```powershell
.\gradlew.bat flowTest          # chạy Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # giải mã một bảng master (Rijndael-256/gzip/JSON) và kiểm tra cấu trúc
```

### Kết nối client thật

> **⚠️ Mở cổng — Cloudflare tunnels KHÔNG hoạt động ở đây.** Khác với GC tiêu chuẩn (một quick tunnel `cloudflared` đơn giản có thể expose bình thường), transport client của RenaGC-Awanotsu là **HTTP/2** end-to-end, với redirect cố định channel vào một `:authority` cụ thể (và trong cấu hình hoạt động là **h2c** plaintext). Quick tunnel Cloudflare không truyền đúng kiểu này (đã xác nhận bằng thử nghiệm). Hãy dùng **LAN trực tiếp `IP:port`** (ví dụ `adb reverse` / redirect proxy trong cùng `/24`) hoặc **tunnel raw-TCP / passthrough HTTP-2 đầy đủ**. Xem [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

Tạo tham chiếu `id → name` cho mọi bảng master (item, bài hát, thẻ member/support, nhân vật, band, stamp, v.v.) cùng danh sách lệnh console:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

Tên được phân giải qua bảng `MasterText` (có các cột tiếng Nhật / tiếng Anh / tiếng Trung phồn thể / tiếng Trung giản thể / tiếng Hàn); trong CBT1, hầu hết ô không phải tiếng Nhật chưa được dịch, nên tên sẽ fallback về tiêu đề tiếng Nhật gốc. Xem [docs/Commands.md](Commands.md) để biết tham chiếu lệnh.

## Khắc phục sự cố

Các vấn đề thường gặp và chi tiết kết nối/redirect nằm trong [docs/Troubleshooting.md](Troubleshooting.md) và [docs/Running.md](Running.md).
