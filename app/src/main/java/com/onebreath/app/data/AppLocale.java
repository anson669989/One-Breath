package com.onebreath.app.data;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * App 內的介面語言（跟隨系統／繁體中文／English）。
 * 不用 AppCompat：每個 Activity 在 attachBaseContext 用 {@link #wrap} 包一層指定語系的 Context；
 * 不是 Activity 的程式要取字串時，也先用 {@link #wrap} 包一層（例如 GroupStore 搬移舊資料時）。
 * 每次呼叫都重新讀 Prefs，所以改了語言不必重開機或重開無障礙服務。
 */
public final class AppLocale {

    public static final String SYSTEM = "system";
    public static final String ZH = "zh";
    public static final String EN = "en";

    private AppLocale() {
    }

    /** 依目前的語言設定回傳要用的 Locale；跟隨系統時回傳 null。 */
    static Locale localeFor(String lang) {
        if (ZH.equals(lang)) return Locale.TRADITIONAL_CHINESE; // zh-TW → values-zh
        if (EN.equals(lang)) return Locale.ENGLISH;              // en → values（預設）
        return null;
    }

    /**
     * 回傳套用了 App 語言設定的 Context；跟隨系統時原樣回傳 base。
     * Configuration.setLocale 與 createConfigurationContext 都是 API 17，minSdk 26 不需要版本判斷；
     * API 24 以上 setLocale 也會一併把 LocaleList 設成只有這一個語系。
     */
    public static Context wrap(Context base) {
        if (base == null) return null;
        Locale locale = localeFor(new Prefs(base).getLanguage());
        if (locale == null) return base;
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        conf.setLocale(locale);
        return base.createConfigurationContext(conf);
    }
}
