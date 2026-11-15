# 喘口氣（One Breath）

[English](README.en.md) | 繁體中文

自製的 one sec：打開你選的分心 App 時，先跳出深呼吸畫面，等一小段時間後可以選「繼續」或「算了」。不會鎖死，急用時等幾秒就能進去。

## 功能
- **開啟前喘口氣**：打開分心 App 前跳出深呼吸動畫＋反思問句，並顯示「這是你今天第 N 次打開」
- **使用中提醒**：一直停在同一個 App 時，每用滿 N 分鐘（1～60 分鐘）再跳出來問你還要不要繼續；這個提醒畫面有自己的等待秒數
- **群組**：把 App 分組（例如「社群」「影音」「遊戲」），每組各自決定要不要「開啟前喘口氣」「使用中提醒」「網站提醒」，可以用全域時間，也可以用自己的時間。一個 App 只會在一個群組裡
- **網站提醒**：選了有網站的 App（例如 X → x.com、twitter.com），用瀏覽器開那個網站也會提醒；也可以在群組裡自訂沒有 App 的網站（例如 `news.example.com`）
- 等待時間可調（0～30 秒，0 = 不用等）；畫面上刻意不顯示倒數秒數或進度，不讓你盯著時間等
- 按「繼續」後 N 分鐘內不再提醒（1～30 分鐘），避免一直被打斷
- 首頁看今天喘了幾口氣、幾次決定不開了；在設定頁最下面可以一鍵暫停全部提醒
- **介面語言**：繁體中文與 English。在設定頁選「跟隨系統」（預設，手機是任何中文語系就顯示繁體中文，其他語言顯示英文）、「繁體中文」或「English」，選了立刻套用。桌面上的 App 名稱和系統無障礙設定裡的說明文字由手機系統語言決定，不受這個設定影響
- **隱私**：沒有網路權限、沒有廣告，所有設定和次數只存在手機上，不會傳出去。無障礙權限只用來知道前景是哪個 App（App 名稱）；在瀏覽器裡只讀取網址列的網址，用來認出你選的網站，畫面上其他內容一律不讀

## 畫面
- **首頁**：無障礙權限狀態（沒開會教你怎麼開）、今天的次數、群組摘要
- **群組**：每個群組一張卡片（App 圖示、開了哪些功能、群組開關）；點進去編輯名稱、功能、時間、自訂網站，並在「App」分頁搜尋、勾選要放進這組的 App
- **設定**：全域時間（開啟前等待秒數、繼續後不再提醒的分鐘數、使用中提醒間隔與等待秒數）、語言（跟隨系統／繁體中文／English）、關於與隱私說明，最下面是「啟用提醒」總開關

## 取得 APK（不用裝 Android Studio）
1. 註冊／登入 GitHub，按右上角「+」→「New repository」，建一個新的 repo（設 Private 也可以）
2. 在 repo 頁面按「uploading an existing file」，把這個資料夾裡**所有檔案和資料夾**拖進去上傳
   - 注意 `.github` 資料夾一定要傳到（它是隱藏資料夾，Windows 要開「顯示隱藏的項目」、Mac 按 Cmd+Shift+.）
3. 設定簽章金鑰（只要做一次，見下方「正式版簽章」）
4. 點上方「Actions」分頁，會看到「Build APK」在跑，約 3～5 分鐘
5. 跑完（綠色勾勾）點進去，最下面「Artifacts」有 `OneBreath-apk`，下載解壓縮就是已簽章的正式版 APK

## 正式版簽章
APK 由 GitHub Actions 建成**正式版（release）**，並用同一把金鑰簽章，所以之後的新版可以直接覆蓋安裝，設定會保留。金鑰不放在 repo 裡，而是存在 GitHub 的 Secrets：

repo 頁面 → Settings → Secrets and variables → Actions → New repository secret，新增兩個：

| 名稱 | 內容 |
| --- | --- |
| `KEYSTORE_BASE64` | 金鑰檔（`.jks`）轉成 base64 的文字 |
| `KEYSTORE_PASSWORD` | 金鑰的密碼 |

金鑰檔和密碼請自己備份好：弄丟的話，之後建出來的 APK 就無法覆蓋安裝舊版（要先解除安裝）。金鑰別名固定是 `onebreath`。

第一次從 debug 版換成正式版時，簽章不同，要先解除安裝手機上的舊版。

## 安裝到三星手機
1. 把 APK 傳到手機，點開安裝（第一次會要你允許「安裝未知應用程式」）
2. 打開「喘口氣」，在首頁按 **① 開啟無障礙權限（找「喘口氣」）** → 已安裝的應用程式 → 喘口氣 → 開啟
3. **開關是灰色的？** 這是 Android 對自行安裝 App 的保護：
   回到 App 按「開啟 App 資訊頁」→ 右上角 ⋮ →「允許受限制的設定」→ 再回去開無障礙
4. 到「群組」頁按「＋ 新增群組」，選一個範本（社群、影音…）或自訂名稱，在「App」分頁勾選要提醒的 App（例如 Instagram、YouTube、Threads），按「儲存」
5. 建議：打開**手機的系統設定**（不是喘口氣 App 裡的「設定」分頁）→ 電池 → 喘口氣 → 設為「不受限制」，避免被三星省電機制關掉

從舊版升級：原本勾選的 App 會自動放進一個叫「我的 App」的群組（升級時介面是英文的話，叫「My apps」；之後就跟其他群組名稱一樣，不會隨語言改變）。

## 檔案結構
`app/src/main/java/com/onebreath/app/`
- `data/`：資料與設定
  - `Prefs.java`：全域設定（含介面語言）、「繼續」後的免打擾時間、每日次數
  - `AppLocale.java`：依語言設定替 Activity 和其他需要字串的地方套上指定語系（跟隨系統／繁體中文／English）
  - `Group.java`、`GroupStore.java`：群組與存取（存在手機的 SharedPreferences）
  - `Rule.java`：某個 App／網站最後套用的設定
  - `SiteCatalog.java`：內建的 App → 網站對照表、網址整理
- `service/`：背景偵測
  - `PauseService.java`：無障礙服務，偵測切換到哪個 App、使用中提醒計時
  - `BrowserUrlReader.java`：只讀瀏覽器網址列，認出要提醒的網站
  - `Sessions.java`：使用者選「繼續」或「算了」之後的處理
- `ui/`：畫面
  - `PauseActivity.java`、`PauseOrbView.java`：深呼吸畫面與呼吸動畫
  - `MainActivity.java`：主畫面與下方導覽列；`HomePage.java`、`GroupsPage.java`、`SettingsPage.java` 是三個分頁
  - `GroupEditActivity.java`、`AppListAdapter.java`：編輯群組與挑選 App
  - `SeekRow.java`：設定頁和群組編輯頁共用的滑桿列（SeekBar 加上即時數值文字，例如「5 秒」「每 10 分鐘」）
  - `UiKit.java`：共用小工具：dp 換算、檢查無障礙服務是否已開啟、群組卡片上的功能標籤、分段切換的樣式、整列點擊切換開關

`app/src/main/res/`
- `values/strings.xml`：英文介面文字（預設，非中文語系時使用）
- `values-zh-rTW/strings.xml`：繁體中文介面文字
