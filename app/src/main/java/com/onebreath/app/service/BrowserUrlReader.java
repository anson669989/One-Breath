package com.onebreath.app.service;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 只讀瀏覽器「網址列」那一格文字，畫面上其他內容一概不讀。
 * 每個瀏覽器一行：套件名稱 → 可能的網址列 view id。
 */
final class BrowserUrlReader {

    private static final Map<String, String[]> URL_BAR_IDS = new HashMap<>();

    static {
        // Chrome / Chromium 系
        add("com.android.chrome", "url_bar");
        add("com.chrome.beta", "url_bar");
        add("com.chrome.dev", "url_bar");
        add("com.chrome.canary", "url_bar");
        add("com.brave.browser", "url_bar");
        add("com.microsoft.emmx", "url_bar");
        add("com.vivaldi.browser", "url_bar");
        add("com.kiwibrowser.browser", "url_bar");
        add("com.opera.browser", "url_field");
        add("com.opera.mini.native", "url_field");
        // Samsung Internet
        add("com.sec.android.app.sbrowser", "location_bar_edit_text", "sbrowser_url_bar");
        add("com.sec.android.app.sbrowser.beta", "location_bar_edit_text", "sbrowser_url_bar");
        // Firefox 系
        add("org.mozilla.firefox", "mozac_browser_toolbar_url_view", "url_bar_title");
        add("org.mozilla.firefox_beta", "mozac_browser_toolbar_url_view", "url_bar_title");
        add("org.mozilla.fenix", "mozac_browser_toolbar_url_view", "url_bar_title");
        add("org.mozilla.focus", "mozac_browser_toolbar_url_view", "url_bar_title");
        // DuckDuckGo
        add("com.duckduckgo.mobile.android", "omnibarTextInput");
    }

    private BrowserUrlReader() {
    }

    private static void add(String pkg, String... ids) {
        String[] full = new String[ids.length];
        for (int i = 0; i < ids.length; i++) full[i] = pkg + ":id/" + ids[i];
        URL_BAR_IDS.put(pkg, full);
    }

    static boolean isBrowser(String pkg) {
        return pkg != null && URL_BAR_IDS.containsKey(pkg);
    }

    /**
     * 回傳網址列目前的文字。找不到網址列、或使用者正在網址列打字時回傳 null（= 不確定，維持原狀）。
     * root 由呼叫端負責回收。
     */
    static String read(AccessibilityNodeInfo root, String pkg) {
        if (root == null || pkg == null) return null;
        String[] ids = URL_BAR_IDS.get(pkg);
        if (ids == null) return null;
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes == null || nodes.isEmpty()) continue;
            String result = null;
            boolean found = false;
            for (AccessibilityNodeInfo node : nodes) {
                if (node == null) continue;
                if (!found) {
                    found = true;
                    if (!node.isFocused() && !node.isShowingHintText()) {
                        CharSequence text = node.getText();
                        if (text != null) result = text.toString();
                    }
                }
                node.recycle();
            }
            if (found) return result;
        }
        return null;
    }
}
