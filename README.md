# 喘口氣（One Breath）

自製的 one sec：打開你勾選的 App 時，先跳出深呼吸畫面，倒數幾秒後可以選「繼續開」或「算了」。不會鎖死，急用時等幾秒就能進去。

## 功能
- 打開分心 App 前跳出深呼吸動畫＋反思問句
- 顯示「這是你今天第 N 次打開」
- 倒數秒數可調（0～30 秒，0 = 不用等）
- 按「繼續」後 N 分鐘內不再提醒（1～30 分鐘），避免一直被打斷
- 一鍵暫停全部提醒
- 沒有網路權限、沒有廣告、不讀畫面內容，設定只存在手機上

## 取得 APK（不用裝 Android Studio）
1. 註冊／登入 GitHub，按右上角「+」→「New repository」，建一個新的 repo（設 Private 也可以）
2. 在 repo 頁面按「uploading an existing file」，把這個資料夾裡**所有檔案和資料夾**拖進去上傳
   - 注意 `.github` 資料夾一定要傳到（它是隱藏資料夾，Windows 要開「顯示隱藏的項目」、Mac 按 Cmd+Shift+.）
3. 上傳後點上方「Actions」分頁，會看到「Build APK」在跑，約 3～5 分鐘
4. 跑完（綠色勾勾）點進去，最下面「Artifacts」有 `OneBreath-apk`，下載解壓縮就是 APK

有 Android Studio 的話，直接用它打開這個資料夾 → Build → Build APK 也可以。

## 安裝到三星手機
1. 把 APK 傳到手機，點開安裝（第一次會要你允許「安裝未知應用程式」）
2. 打開「喘口氣」，按「開啟無障礙權限」→ 已安裝的應用程式 → 喘口氣 → 開啟
3. **開關是灰色的？** 這是 Android 對自行安裝 App 的保護：
   回到 App 按「開啟 App 資訊頁」→ 右上角 ⋮ →「允許受限制的設定」→ 再回去開無障礙
4. 勾選要提醒的 App（例如 Instagram、YouTube、Threads）
5. 建議：設定 → 電池 → 喘口氣 → 設為「不受限制」，避免被三星省電機制關掉

## 檔案結構
- `PauseService.java`：偵測切換到哪個 App
- `PauseActivity.java`：深呼吸畫面
- `MainActivity.java`：設定頁
- `Prefs.java`：設定與每日次數
