# 佳里血糖報告

臺南市佳里區衛生所 指尖血糖報告 Android App（Galaxy S22 Ultra 自用 sideload）。

- 輸入姓名、指尖血糖，檢驗時間自動帶入民國年（可調）
- 報告圖含試片廠牌／批號／效期（右上「⚙ 試片」修改）
- 「存檔並傳送 LINE」：存入相簿 Pictures/佳里血糖，再分享圖文至 LINE

## Build

push `main` 後 GitHub Actions 自動打包，APK 在 Releases。升版改 `app/build.gradle.kts` 的 versionCode / versionName。
