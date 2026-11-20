# 拾旅

拾旅是一款記錄旅行與日常、整理旅費的 Android 應用程式。`docs/` 提供可以直接在瀏覽器操作的試用版。

## 瀏覽器試用版

內建「南投・湖畔兩日」與「日本關西五日」兩趟範例。日本範例涵蓋大阪、京都、奈良的每日行程，以及兩筆日圓分帳，採用示範匯率 1 JPY = 0.215 TWD。訪客也可以建立旅程、新增沿途紀錄與私人日誌，並試算平均分帳和指定金額分帳。支援以 TWD、JPY 或 USD 輸入支出，統一換算為 TWD。每趟旅程的分帳使用固定的三位示範成員。

旅程、日誌與支出儲存在目前瀏覽器的本機儲存空間。試用版涵蓋文字紀錄和分帳，Android 版的相機、錄音和定位功能需在手機使用。

### 本機預覽

在倉庫根目錄執行以下指令，再以瀏覽器開啟 `http://localhost:4173`。

```sh
python3 -m http.server 4173 --bind 127.0.0.1 --directory docs
```

### 發布到 GitHub Pages

1. 將包含 `docs/` 的變更合併到 `main`。
2. 在 GitHub 倉庫開啟 **Settings → Pages**。
3. 在 **Build and deployment** 將來源設為 **Deploy from a branch**，選擇 `main` 和 `/docs` 後儲存。
4. 部署完成後，從 Pages 設定頁開啟 GitHub 顯示的網站網址。

## Android 版

使用 JDK 17 與 Android SDK 34，在倉庫根目錄執行以下指令建置。

```sh
bash gradlew :app:assembleDebug
```

輸出位於 `app/build/outputs/apk/debug/app-debug.apk`。
