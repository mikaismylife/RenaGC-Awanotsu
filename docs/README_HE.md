# RenaGC-Awanotsu
![RenaGC-Awanotsu](https://socialify.git.ci/aosumi-rena/RenaGC-Awanotsu/image?description=1&logo=https://avatars.githubusercontent.com/u/119492185?v=4&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Dark)

<div align="center">המשך רוחני של <b>אמולטור השרת</b> Grasscutter עבור <i>משחק קצב מסוים</i>.</div>

[EN](../README.md) | [简中](README_zh-CN.md) | [繁中](README_zh-TW.md) | [日本語](README_ja-JP.md) | [한국어](README_ko-KR.md) | [FR](README_fr-FR.md) | [ES](README_es-ES.md) | [RU](README_ru-RU.md) | [PL](README_pl-PL.md) | [ID](README_id-ID.md) | [IT](README_it-IT.md) | [VI](README_vi-VN.md) | [NL](README_NL.md) | [HE](README_HE.md) | [FIL/PH](README_fil-PH.md)

> **Attention**: אנו תמיד מקבלים בברכה תורמים לפרויקט.

## תכונות נוכחיות

* התחברות
* Free lives
* Master-data CDN
* Asset CDN
* Remote GC console
* יצירת חשבונות
* שליטה במצב בצד השרת

## במה זה שונה מ-Grasscutter — שרת היברידי, לא GC טהור

RenaGC-Awanotsu משתמש מחדש ב*צורה* של Grasscutter (קונסול עם מטפלי `@Command`, סריקת `CommandMap`, יצירת חשבונות שמונעת על ידי master DB, מחולל GM Handbook), אבל הוא **אינו** fork של Grasscutter, ושכבת התקשורת שונה לחלוטין:

| | Grasscutter | RenaGC-Awanotsu |
|---|---|---|
| Transport | KCP/UDP + שרת HTTP של dispatch | **gRPC + Protobuf** מעל HTTP/2 (h2c `:20000`, TLS `:443`) |
| "Dispatch" | שרת dispatch/region אמיתי | **אין** — שני hosts: gRPC + CDN |
| Master data | משאבי Excel/Lua מקומיים | **טבלאות `*.bin` מוצפנות** שהלקוח מוריד מ-CDN ‏(Rijndael-256) |
| Game assets | מקומיים בצד הלקוח | **Unity Addressables** (בתוך החבילה + CDN של bundle מרוחק) |

## מדריך התקנה מהיר

### דרישות

* **[JDK 21](https://adoptium.net/)**
* **[Git](https://git-scm.com/downloads)**
* **[MongoDB](https://www.mongodb.com/try/download/community)**
* **[Resources](https://minas.mihoyo.day/f/3ab8c5af2648408683d7/?dl=1)** — חלצו לשורש המאגר, ואז מלאו ב-`config.json` את `masterdata.keyHex` המקומי בלבד

### בנייה והרצה

```powershell
.\gradlew.bat build   # מקמפל + מייצר את 36 שירותי ה-gRPC מתוך recovered protos
.\gradlew.bat run     # מפעיל את השרת (קורא את config.json)
```

| פורט | מטרה |
|------|------|
| `:20000` | gRPC **h2c** (HTTP/2 גלוי) — בדיקות מקומיות + `grpcurl` |
| `:443` | gRPC **TLS** (חתימה עצמית, עבור הלקוח האמיתי במכשיר) |
| `:5080` / `:8443` | Master-data CDN (HTTP / HTTPS) |
| `:5081` / `:8444` | Asset CDN (HTTP / HTTPS) |
| `:5090` | Remote GC console (HTTP) |

### אימות

```powershell
.\gradlew.bat flowTest          # עובר דרך Version → Register → GetPlayerData → Home → StartFree → Finish → Ranking
.\gradlew.bat masterdataSmoke   # מפענח טבלת master (Rijndael-256/gzip/JSON) ומוודא את המבנה שלה
```

### חיבור הלקוח האמיתי

> **⚠️ חשיפת הפורט — Cloudflare tunnels אינם עובדים כאן.** בניגוד ל-GC סטנדרטי (ש-quick tunnel פשוט של `cloudflared` חושף בלי בעיה), תעבורת הלקוח של RenaGC-Awanotsu היא **HTTP/2** מקצה לקצה, וה-redirect מקבע את הערוץ ל-`:authority` מסוים (ובתצורה שעובדת, **h2c** גלוי). Cloudflare quick tunnel לא מעביר זאת כראוי (אומת בבדיקות שלנו). השתמשו במקום זאת ב-**`IP:port` ישיר ברשת LAN** (לדוגמה `adb reverse` / redirect של proxy באותו `/24`) או ב-**מנהרת raw-TCP / passthrough מלא של HTTP-2**. ראו [docs/Running.md](Running.md#exposing-the-server).

## GM Handbook

צרו הפניה `id → name` עבור כל טבלת master (פריטים, שירים, כרטיסי member/support, דמויות, להקות, stamps וכו') יחד עם רשימת פקודות הקונסול:

```powershell
.\gradlew.bat generateHandbook                 # → "GM Handbook/GM Handbook - EN.txt"
.\gradlew.bat generateHandbook --args="CHS"    # also: JP, CHT, KR
```

השמות נפתרים דרך טבלת `MasterText` (שכוללת עמודות יפנית / אנגלית / סינית מסורתית / סינית מפושטת / קוריאנית); ב-CBT1 רוב התאים שאינם יפניים אינם מתורגמים, ולכן השמות חוזרים לכותרת היפנית המקורית. ראו [docs/Commands.md](Commands.md) להפניית הפקודות.

## פתרון בעיות

בעיות נפוצות ופרטי חיבור/redirect נמצאים ב-[docs/Troubleshooting.md](Troubleshooting.md) וב-[docs/Running.md](Running.md).
