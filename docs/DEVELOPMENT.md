# 喘口氣 開發說明

[English](DEVELOPMENT.en.md) | 繁體中文 · [回到 README](../README.md)

這份文件寫給想自己建置、修改或參與開發的人。App 的介紹和一般安裝步驟在 [README](../README.md)。

## 環境需求
| 項目 | 值 | 出處 |
| --- | --- | --- |
| minSdk | 26（Android 8.0） | `app/build.gradle` |
| targetSdk／compileSdk | 34 | `app/build.gradle` |
| Java | 17（`sourceCompatibility`／`targetCompatibility`） | `app/build.gradle` |
| Android Gradle Plugin | 8.7.3 | `build.gradle` |
| Gradle | 8.11.1（wrapper 與 CI 相同） | `gradle/wrapper/gradle-wrapper.properties`、`.github/workflows/main.yml` |
| 版本 | versionName `2.1`、versionCode `4` | `app/build.gradle` |

- 純 Java + XML layout，沒有 Compose，也沒有任何第三方函式庫（`dependencies` 是空的），連 AndroidX／AppCompat 都沒用（`gradle.properties` 裡 `android.useAndroidX=false`）。
- 新增依賴之前請先想清楚：App 刻意只用 Android 內建 API。

## 在自己電腦上建置
需要 Android SDK。用 `ANDROID_HOME` 環境變數，或在專案根目錄的 `local.properties`（已在 `.gitignore`）寫 `sdk.dir=...` 指向 SDK。

```sh
./gradlew assembleDebug      # Windows：.\gradlew.bat assembleDebug
```

APK 會在 `app/build/outputs/apk/debug/`。

正式版（release）：設定環境變數 `KEYSTORE_FILE`（`.jks` 檔路徑）和 `KEYSTORE_PASSWORD` 之後執行 `assembleRelease`。沒有設定、或 `KEYSTORE_FILE` 指的檔案不存在時，release 會建成**未簽章**的 APK，不能直接安裝。

## 用 GitHub Actions 建置（不用裝 Android Studio）
1. 註冊／登入 GitHub，按右上角「+」→「New repository」，建一個新的 repo（設 Private 也可以）
2. 在 repo 頁面按「uploading an existing file」，把專案資料夾裡**所有檔案和資料夾**拖進去上傳
   - 注意 `.github` 資料夾一定要傳到（它是隱藏資料夾，Windows 要開「顯示隱藏的項目」、Mac 按 Cmd+Shift+.）
3. 設定簽章金鑰（只要做一次，見下方「正式版簽章」）。**沒設定的話建置會直接失敗。**
4. 點上方「Actions」分頁，會看到「Build APK」在跑，約 3～5 分鐘
5. 跑完（綠色勾勾）點進去，最下面「Artifacts」有 `OneBreath-apk`，下載解壓縮就是已簽章的正式版 APK

### Workflow 做了什麼
`.github/workflows/main.yml`（名稱「Build APK」）：

- **觸發**：每次 push，以及在 Actions 頁面手動執行（`workflow_dispatch`）。
- 在 `ubuntu-latest` 上裝 Temurin JDK 17，用 `gradle/actions/setup-gradle` 裝 Gradle 8.11.1。CI 直接執行 `gradle`，不是 `./gradlew`。
- **Restore signing key**：把 Secret `KEYSTORE_BASE64` 解碼成 `$RUNNER_TEMP/release.jks`，並設定 `KEYSTORE_FILE`。兩個 Secret 缺任何一個，這一步就以錯誤結束。
- **Build signed release APK**：找到 `settings.gradle` 所在的資料夾後執行 `gradle assembleRelease`。
- 上傳 `**/build/outputs/apk/release/*.apk` 成為名為 `OneBreath-apk` 的 artifact；找不到 APK 時這一步會失敗。Artifact 要登入 GitHub 才能下載，保留期限依 repo 設定，過期會自動刪除。
- Workflow 不會建立 GitHub Release，也不會上架任何商店。

## 正式版簽章
APK 由 GitHub Actions 建成**正式版（release）**，並用同一把金鑰簽章，所以之後的新版可以直接覆蓋安裝，設定會保留。金鑰不放在 repo 裡，而是存在 GitHub 的 Secrets：

repo 頁面 → Settings → Secrets and variables → Actions → New repository secret，新增兩個：

| 名稱 | 內容 |
| --- | --- |
| `KEYSTORE_BASE64` | 金鑰檔（`.jks`）轉成 base64 的文字 |
| `KEYSTORE_PASSWORD` | 金鑰的密碼 |

- 金鑰別名固定是 `onebreath`；金鑰密碼和金鑰庫密碼用同一個 `KEYSTORE_PASSWORD`（見 `app/build.gradle` 的 `signingConfigs`）。
- 金鑰檔和密碼請自己備份好：弄丟的話，之後建出來的 APK 就無法覆蓋安裝舊版（要先解除安裝）。
- 第一次從 debug 版換成正式版時，簽章不同，要先解除安裝手機上的舊版。

## 權限
`AndroidManifest.xml` 裡**沒有任何 `<uses-permission>`**，所以沒有網路權限。用到的只有：

- 無障礙服務 `PauseService`（`android.permission.BIND_ACCESSIBILITY_SERVICE`，這是系統綁定服務用的，不是向使用者要的權限；使用者要自己到系統設定開啟）。
- `<queries>`：宣告會查詢「有啟動畫面的 App」（`MAIN`＋`LAUNCHER`），Android 11 以上要有這個才能在群組編輯頁列出 App。
- 螢幕開關的廣播（`SCREEN_OFF`、`SCREEN_ON`、`USER_PRESENT`）是在服務裡動態註冊的，Android 13 以上用 `RECEIVER_NOT_EXPORTED`。

注意：`android:allowBackup="true"`，所以使用者開了 Android 系統備份時，SharedPreferences（設定、群組、次數）可能會被系統備份。App 本身不會傳任何東西出去。

## 程式怎麼運作

### 無障礙服務（`service/PauseService.java`）
- 設定在 `res/xml/accessibility_config.xml`：監聽 `typeWindowStateChanged|typeWindowContentChanged`，`flagReportViewIds`、`canRetrieveWindowContent="true"`，`notificationTimeout` 100 ms。
- 「目前目標」可能是一個 App（套件名稱），或一個網站（`web:網域`）。
- `TYPE_WINDOW_STATE_CHANGED` 代表最前面的 App 可能換了。系統介面（`com.android.systemui`、權限對話框、三星側邊面板等，見 `IGNORE`）、輸入法、Toast 都不算切換。
- 自己的畫面（喘口氣畫面、設定頁）在最前面時，目前目標暫停計時，但不算「用完了」。
- 切進一個目標時：如果群組開了「開啟前喘口氣」而且不在寬限期內，就開 `PauseActivity`（開啟模式）；否則直接交給 `Sessions` 開始計時。系統不讓畫面跳出來時，安靜放行，絕不卡住使用者。
- 螢幕關掉：停止計時，也不會對著黑螢幕跳提醒。解鎖回來：繼續計時，不會重新跳開啟前畫面。
- 處理事件時的例外全部吞掉：服務一旦當掉就會被系統關閉，寧可漏掉一次。

### 瀏覽器網址列（`service/BrowserUrlReader.java`）
- 只在「最前面是已知的瀏覽器」而且「至少有一個啟用的群組可能對上網站」時才讀。`TYPE_WINDOW_CONTENT_CHANGED` 非常頻繁，所以最多每 400 ms 讀一次，最後一次變化之後一定會再讀一次。
- 只用 view id 找網址列那一格（`findAccessibilityNodeInfosByViewId`），畫面上其他內容一概不讀。網址列正在輸入（有焦點）或只顯示提示文字時，當作「不確定」，維持原狀。
- 支援的瀏覽器（套件名稱 → 網址列 id）：Chrome（含 Beta／Dev／Canary）、Brave、Edge、Vivaldi、Kiwi、Opera、Opera Mini、Samsung Internet（含 Beta）、Firefox（含 Beta、Nightly `org.mozilla.fenix`）、Firefox Focus、DuckDuckGo。要支援新的瀏覽器，在 `URL_BAR_IDS` 加一行。
- 如果瀏覽器本身也在某個群組裡、而且它的開啟前畫面正要跳出來，App 的規則優先。

### 使用中提醒的計時（`service/Sessions.java`）
- 狀態只放在記憶體（整個 process 共用），process 被系統收掉就重新開始算。
- 只累計目標在最前面的時間。離開後在「繼續後不再提醒」的分鐘數內回來，算同一段使用；超過就重新算。
- 計時到了，用目前連著的無障礙服務開 `PauseActivity`（提醒模式），帶上已用分鐘數；同時先排好下一輪，萬一畫面沒跳出來也不會一直重複觸發。
- 在提醒畫面選「再用一下」：重新倒數一輪，總使用時間保留。
- 選放棄（按鈕、返回鍵，或離開畫面）：清掉這個目標的寬限期和計時，並記一次「決定不開了」。
- 服務被關掉時停止所有計時。

### 喘口氣畫面（`ui/PauseActivity.java`、`ui/PauseOrbView.java`）
- 兩種模式：開啟前（`MODE_OPEN`）和使用中提醒（`MODE_REMIND`，暖色調）。`singleTask`、獨立的 `taskAffinity`、不出現在最近使用。
- 等待時間取自該目標的規則（群組自訂時間或全域設定），用 `elapsedRealtime` 計時、不靠動畫，所以系統關閉動畫時一樣準時。時間到了，繼續的文字按鈕才淡入，之前完全不能按。
- 開啟模式每次出現都會讓「今天打開這個目標的次數」和「今天總次數」各加一。
- 按返回鍵 = 放棄並回到桌面。按 Home、切到別的 App 或關螢幕（`onStop`）= 視同放棄並收掉畫面，也算一次「決定不開了」，下次打開再重新提醒。
- 選「繼續」：開始寬限期（`Prefs.startGrace`），開啟模式下會直接幫你打開那個 App。
- 呼吸光球是自繪的 View：4 秒吸氣、4 秒吐氣，刻意不顯示任何倒數或進度。提示字太長（例如英文）時會自動縮小到放得進球心。

### 資料與設定（`data/`）
- 全部存在 SharedPreferences 檔 `settings`：
  - 全域設定：`enabled`（總開關）、`delay_seconds`（預設 5，0～30）、`grace_minutes`（預設 5，1～30）、`remind_minutes`（預設 10，1～60）、`remind_delay_seconds`（預設 5，0～30）、`language`（`system`／`zh`／`en`）。
  - 寬限期：`grace_until_<目標>`。若手機時間被往回調，寬限期不會因此變得超長。
  - 每日次數：`count_day`（`yyyyMMdd`，手機本地時間）、`today_total`、`today_resisted`、`cnt_<目標>`。換日時自動歸零。
  - 群組：`groups_v2`，一個 JSON 陣列（`id`、`name`、`enabled`、`pauseOnOpen`、`remindInUse`、`blockWeb`、`customTiming`、`delaySeconds`、`remindMinutes`、`remindDelaySeconds`、`apps`、`sites`）。
- `GroupStore` 是整個 App 共用的一份，記憶體快取加上預先建好的對照表，因為服務每收到一次事件都會查。
- 一個 App 只能在一個群組：存檔時會從其他群組移除，讀取時也會去掉重複。
- 新群組預設三個功能都開、使用全域時間（時間欄位先填入目前的全域設定）。
- `Rule` 是某個目標最後套用的設定。群組沒自訂時間時，每次都重新讀全域設定。
- 網站比對：先比群組的自訂網站，再比內建對照表 `SiteCatalog`（約 30 個常見 App，例如 X → x.com、twitter.com）。同一個 App 的多個網域會對到同一個「代表網域」（第一個），換網域時不會重複跳出。子網域也算（`m.x.com` 對得上 `x.com`）。`SiteCatalog.normalize` 會去掉 scheme、路徑、埠號和開頭的 `www.`，不是網址的文字（搜尋字、`about:blank` 等）一律不算。
- 從 v1 升級：v1 只有一個 App 清單（`apps`）。第一次載入時如果還沒有群組，就把它搬進一個新群組，名稱是當下介面語言的「我的 App」／“My apps”，然後刪掉舊的鍵。之後這個名稱就是使用者資料，不會跟著語言改。

### 畫面（`ui/`）
- `MainActivity` 用 `<include>` 放三個分頁（首頁／群組／設定），由 `HomePage`、`GroupsPage`、`SettingsPage` 各自負責；切換分頁和 `onResume` 時重新整理目前的分頁。目前分頁存在 `onSaveInstanceState`，所以旋轉或換語言重建後會留在原本那頁。
- 「啟用提醒」總開關在設定頁最下面。首頁每次顯示時重新讀它的狀態，暫停中會在群組摘要上註明。
- `GroupEditActivity`：上方「設定｜App」兩段切換；App 段用 `ListView` 自己捲動（App 可能有兩三百個），App 清單在背景執行緒載入。只有按「儲存」才寫入；有改動時按返回會先問要不要儲存。旋轉時保留編輯中的內容。

### 語言（`data/AppLocale.java`）
- 英文放在 `res/values/strings.xml`（預設），繁體中文放在 `res/values-zh-rTW/strings.xml`。兩份檔案的鍵必須完全一致，格式參數（`%1$s`、`%1$d`）也要對得上；英文需要單複數的地方用 `<plurals>`。
- **為什麼是 `values-zh-rTW` 而不是 `values-zh`**：Android 7 以上把沒標地區的 `values-zh` 當成簡體（zh-Hans），繁體語系（zh-Hant，例如 zh-TW）配不到它，會掉回英文。所以中文只放在 `values-zh-rTW`，而且不靠系統自己配對：
  - 選「繁體中文」：套用 zh-TW。
  - 選「English」：套用 `en`。
  - 選「跟隨系統」：系統的**第一個**語言是任何一種中文（zh-TW、zh-HK、zh-CN…）時套用 zh-TW，否則不動（→ 英文）。只看第一個語言，例如系統語言清單是「日文、中文」時會顯示英文。
- 套用方式：`AppLocale.wrap(Context)` 每次都重新讀 Prefs，用 `Configuration.setLocale` 加 `createConfigurationContext` 包一層 Context（API 17 就有，minSdk 26 不需要版本判斷）。沒用 AppCompat。
  - 三個 Activity（`MainActivity`、`GroupEditActivity`、`PauseActivity`）都在 `attachBaseContext` 呼叫它。
  - 不是 Activity 的程式要取字串時也要先包一層，例如 `GroupStore` 搬移 v1 資料時取「我的 App」。
  - 無障礙服務本身不取字串，也不建畫面；喘口氣畫面每次建立都重新讀語言，所以改了語言不必重開服務或重開機。
  - 設定頁換語言後 `recreate()`，並留在設定頁。
- 不受 App 內語言設定影響的地方：桌面上的 App 名稱（`app_name`）和系統無障礙設定裡的服務名稱與說明（`service_description`）是由系統用系統語言解析的。系統語言是簡體中文（zh-CN）時，系統配不到 `values-zh-rTW`，這幾處可能會顯示英文。

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
  - `Sessions.java`：使用中提醒的計時，以及使用者選「繼續」或「算了」之後的處理
- `ui/`：畫面
  - `PauseActivity.java`、`PauseOrbView.java`：深呼吸畫面與呼吸動畫
  - `MainActivity.java`：主畫面與下方導覽列；`HomePage.java`、`GroupsPage.java`、`SettingsPage.java` 是三個分頁
  - `GroupEditActivity.java`、`AppListAdapter.java`：編輯群組與挑選 App
  - `SeekRow.java`：設定頁和群組編輯頁共用的滑桿列（SeekBar 加上即時數值文字，例如「5 秒」「每 10 分鐘」）
  - `UiKit.java`：共用小工具：dp 換算、檢查無障礙服務是否已開啟、群組卡片上的功能標籤、分段切換的樣式、整列點擊切換開關

`app/src/main/res/`
- `values/strings.xml`：英文介面文字（預設）
- `values-zh-rTW/strings.xml`：繁體中文介面文字
- `xml/accessibility_config.xml`：無障礙服務設定
- `layout/`：所有畫面的 XML layout

其他：
- `.github/workflows/main.yml`：GitHub Actions 建置（見上方）
- `app/build.gradle`：版本、SDK 等級、簽章設定
