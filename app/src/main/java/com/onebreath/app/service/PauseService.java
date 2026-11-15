package com.onebreath.app.service;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.onebreath.app.data.GroupStore;
import com.onebreath.app.data.Prefs;
import com.onebreath.app.data.Rule;
import com.onebreath.app.data.SiteCatalog;
import com.onebreath.app.ui.PauseActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 聽「哪個 App 跑到最前面」；在瀏覽器裡只讀網址列那一格，用來認出要提醒的網站。
 * 「目前目標」可能是一個 App（套件名稱）或一個網站（"web:網域"），
 * 從別的東西切進目標時跳出開啟前的喘口氣畫面，待在目標裡的時間交給 Sessions 計算。
 */
public class PauseService extends AccessibilityService {

    /** 這些是系統介面（通知欄、側邊面板等），不算「切換 App」。 */
    private static final Set<String> IGNORE = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "android",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.app.smartcapture",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
    ));

    /** 瀏覽器網址列最多每 400ms 讀一次，最後一次變化之後一定會再讀一次。 */
    private static final long URL_THROTTLE_MS = 400;

    private final Set<String> imePackages = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Prefs prefs;
    private GroupStore store;

    private String lastPkg = "";      // 最前面的 App（含自己）
    private String currentKey;        // 目前在最前面的目標；null = 沒有
    private String currentHost;       // 網站目標所在的瀏覽器
    private boolean screenOff;

    private long lastUrlCheckAt;
    private boolean urlCheckPending;
    private final Runnable urlCheck = () -> {
        urlCheckPending = false;
        safeCheckBrowser();
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                onScreenEvent(intent != null ? intent.getAction() : null);
            } catch (Throwable ignored) {
            }
        }
    };
    private boolean receiverRegistered;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            prefs = new Prefs(this);
            store = GroupStore.get(this);
            loadKeyboards();
            Sessions.attach(this);
            registerScreenReceiver();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        try {
            handler.removeCallbacksAndMessages(null);
            if (currentKey != null) Sessions.leave(currentKey);
            currentKey = null;
            Sessions.detach();
            if (receiverRegistered) {
                receiverRegistered = false;
                unregisterReceiver(screenReceiver);
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private void loadKeyboards() {
        imePackages.clear();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        List<InputMethodInfo> list = imm.getEnabledInputMethodList();
        if (list == null) return;
        for (InputMethodInfo info : list) imePackages.add(info.getPackageName());
    }

    private void registerScreenReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
        receiverRegistered = true;
    }

    private Prefs prefs() {
        if (prefs == null) prefs = new Prefs(this);
        return prefs;
    }

    private GroupStore store() {
        if (store == null) store = GroupStore.get(this);
        return store;
    }

    // ------------------------------------------------------------------ events

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            handleEvent(event);
        } catch (Throwable ignored) {
            // 服務一旦當掉就會被系統關閉；寧可漏掉一次，也不要當掉
        }
    }

    private void handleEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // 這種事件非常頻繁：只在意「最前面的瀏覽器」且真的有網站要比對時
            if (pkg.equals(lastPkg) && BrowserUrlReader.isBrowser(pkg) && store().hasSiteRules()) {
                requestBrowserCheck();
            }
            return;
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        // 系統介面、鍵盤、Toast 小提示都不算切換 App
        if (IGNORE.contains(pkg) || imePackages.contains(pkg)) return;
        CharSequence cls = event.getClassName();
        if (cls != null && cls.toString().contains("Toast")) return;

        onForeground(pkg);
    }

    private void onForeground(String pkg) {
        if (pkg.equals(getPackageName())) {
            // 自己的畫面（喘口氣畫面、設定頁）在最前面：目標暫停計時，但不算「用完了」
            lastPkg = pkg;
            moveTo(null, null);
            return;
        }
        boolean switched = !pkg.equals(lastPkg);
        lastPkg = pkg;
        if (switched) {
            boolean watched = prefs().isEnabled() && store().ruleForApp(pkg) != null;
            moveTo(watched ? pkg : null, null);
        }
        if (BrowserUrlReader.isBrowser(pkg) && store().hasSiteRules()) requestBrowserCheck();
    }

    /** 目前目標換成 newKey：舊的離開，新的進來（必要時先跳出開啟前畫面）。 */
    private void moveTo(String newKey, String hostPkg) {
        if (newKey == null ? currentKey == null : newKey.equals(currentKey)) return;
        if (currentKey != null) Sessions.leave(currentKey);
        currentKey = newKey;
        currentHost = hostPkg;
        if (newKey == null) return;

        Rule rule = store().ruleFor(newKey);
        if (rule == null || !prefs().isEnabled()) return;
        if (rule.pauseOnOpen && !prefs().inGrace(newKey)) {
            if (launch(PauseActivity.intent(this, newKey, PauseActivity.MODE_OPEN, hostPkg, 0))) return;
            // 跳不出畫面就放行，照樣計算使用時間
        }
        Sessions.enter(this, newKey, hostPkg);
    }

    private boolean launch(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            // 萬一系統不讓跳出畫面，就安靜放行，絕不卡住使用者
            return false;
        }
    }

    // ------------------------------------------------------------------ browser

    private void requestBrowserCheck() {
        long now = SystemClock.uptimeMillis();
        long wait = lastUrlCheckAt + URL_THROTTLE_MS - now;
        if (wait <= 0) {
            handler.removeCallbacks(urlCheck);
            urlCheckPending = false;
            safeCheckBrowser();
        } else if (!urlCheckPending) {
            urlCheckPending = true;
            handler.postDelayed(urlCheck, wait);
        }
    }

    private void safeCheckBrowser() {
        try {
            checkBrowser();
        } catch (Throwable ignored) {
        }
    }

    private void checkBrowser() {
        String browser = lastPkg;
        if (!BrowserUrlReader.isBrowser(browser) || screenOff) return;
        lastUrlCheckAt = SystemClock.uptimeMillis();

        boolean on = prefs().isEnabled();
        Rule appRule = on ? store().ruleForApp(browser) : null;
        // 瀏覽器本身就是被提醒的 App、而且畫面正要跳出來：App 的規則優先
        if (appRule != null && appRule.pauseOnOpen && !prefs().inGrace(browser)
                && browser.equals(currentKey)) return;
        String appKey = appRule != null ? browser : null;

        String domain = null;
        if (on && store().hasSiteRules()) {
            String text;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            try {
                CharSequence rootPkg = root.getPackageName();
                if (rootPkg == null || !browser.equals(rootPkg.toString())) return;
                text = BrowserUrlReader.read(root, browser);
            } finally {
                root.recycle();
            }
            if (text == null) return; // 找不到網址列或正在打字：維持原狀
            domain = store().matchSite(SiteCatalog.normalize(text));
        }

        if (domain != null) {
            moveTo("web:" + domain, browser);
        } else {
            moveTo(appKey, null);
        }
    }

    // ------------------------------------------------------------------ screen

    private void onScreenEvent(String action) {
        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            screenOff = true;
            handler.removeCallbacks(urlCheck);
            urlCheckPending = false;
            // 螢幕關了就不再計時，也不會對著黑螢幕跳提醒
            if (currentKey != null) Sessions.leave(currentKey);
            return;
        }
        boolean unlocked = Intent.ACTION_USER_PRESENT.equals(action);
        if (!unlocked && Intent.ACTION_SCREEN_ON.equals(action)) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            unlocked = km == null || !km.isKeyguardLocked();
        }
        if (unlocked && screenOff) {
            screenOff = false;
            // 解鎖後回到原本的畫面：繼續計時（不重新跳開啟前畫面）
            if (currentKey != null) Sessions.enter(this, currentKey, currentHost);
            if (BrowserUrlReader.isBrowser(lastPkg) && store().hasSiteRules()) requestBrowserCheck();
        }
    }
}
