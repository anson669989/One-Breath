package com.onebreath.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 只聽「哪個 App 跑到最前面」這一種事件，不讀畫面內容。
 * 從別的 App 切進你勾選的 App 時，跳出 PauseActivity。
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

    private final Set<String> imePackages = new HashSet<>();
    private String lastPkg = "";
    private Prefs prefs;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = new Prefs(this);
        loadKeyboards();
    }

    private void loadKeyboards() {
        imePackages.clear();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            imePackages.add(info.getPackageName());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();

        // 系統介面、鍵盤、Toast 小提示都不算切換 App
        if (IGNORE.contains(pkg) || imePackages.contains(pkg)) return;
        CharSequence cls = event.getClassName();
        if (cls != null && cls.toString().contains("Toast")) return;

        boolean switched = !pkg.equals(lastPkg);
        lastPkg = pkg;
        if (!switched || pkg.equals(getPackageName())) return;

        if (prefs == null) prefs = new Prefs(this);
        if (!prefs.isEnabled()) return;
        if (!prefs.getApps().contains(pkg)) return;
        if (prefs.inGrace(pkg)) return;

        Intent intent = new Intent(this, PauseActivity.class)
                .putExtra(PauseActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            // 萬一系統不讓跳出畫面，就安靜放行，絕不卡住使用者
        }
    }

    @Override
    public void onInterrupt() {
    }
}
