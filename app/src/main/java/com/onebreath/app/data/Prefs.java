package com.onebreath.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 所有設定都存在手機本機，不會上傳。 */
public final class Prefs {
    static final String FILE = "settings";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DELAY = "delay_seconds";
    private static final String KEY_GRACE = "grace_minutes";
    private static final String KEY_REMIND = "remind_minutes";
    private static final String KEY_REMIND_DELAY = "remind_delay_seconds";
    private static final String KEY_COUNT_DAY = "count_day";
    private static final String KEY_TODAY_TOTAL = "today_total";
    private static final String KEY_TODAY_RESISTED = "today_resisted";
    private static final String PREFIX_GRACE = "grace_until_";
    private static final String PREFIX_COUNT = "cnt_";

    /** 跨所有 Prefs 實例共用，避免換日歸零時互相打架。 */
    private static final Object DAY_LOCK = new Object();

    private final SharedPreferences sp;

    public Prefs(Context context) {
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    SharedPreferences raw() {
        return sp;
    }

    static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    // ---- 總開關 ----

    public boolean isEnabled() {
        return sp.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean on) {
        sp.edit().putBoolean(KEY_ENABLED, on).apply();
    }

    // ---- 時間設定 ----

    /** 開啟前的畫面要等幾秒才能按「繼續」，0 = 不用等。 */
    public int getDelaySeconds() {
        return clamp(sp.getInt(KEY_DELAY, 5), 0, 30);
    }

    public void setDelaySeconds(int s) {
        sp.edit().putInt(KEY_DELAY, clamp(s, 0, 30)).apply();
    }

    /** 按「繼續」後幾分鐘內不再跳開啟提醒。 */
    public int getGraceMinutes() {
        return clamp(sp.getInt(KEY_GRACE, 5), 1, 30);
    }

    public void setGraceMinutes(int m) {
        sp.edit().putInt(KEY_GRACE, clamp(m, 1, 30)).apply();
    }

    /** 連續使用幾分鐘後再提醒一次。 */
    public int getRemindMinutes() {
        return clamp(sp.getInt(KEY_REMIND, 10), 1, 60);
    }

    public void setRemindMinutes(int m) {
        sp.edit().putInt(KEY_REMIND, clamp(m, 1, 60)).apply();
    }

    /** 使用中提醒的畫面要等幾秒才能按「繼續」。 */
    public int getRemindDelaySeconds() {
        return clamp(sp.getInt(KEY_REMIND_DELAY, 5), 0, 30);
    }

    public void setRemindDelaySeconds(int s) {
        sp.edit().putInt(KEY_REMIND_DELAY, clamp(s, 0, 30)).apply();
    }

    // ---- 寬限期 ----

    public void startGrace(String targetKey) {
        if (targetKey == null) return;
        long until = System.currentTimeMillis() + getGraceMinutes() * 60_000L;
        sp.edit().putLong(PREFIX_GRACE + targetKey, until).apply();
    }

    public void clearGrace(String targetKey) {
        if (targetKey == null) return;
        sp.edit().remove(PREFIX_GRACE + targetKey).apply();
    }

    public boolean inGrace(String targetKey) {
        if (targetKey == null) return false;
        long until = sp.getLong(PREFIX_GRACE + targetKey, 0);
        long now = System.currentTimeMillis();
        // 若手機時間被往回調，避免寬限期變得超長
        return now < until && until - now <= getGraceMinutes() * 60_000L + 60_000L;
    }

    // ---- 今日次數 ----

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private boolean isToday() {
        return today().equals(sp.getString(KEY_COUNT_DAY, ""));
    }

    /** 換日了就把今天的次數全部歸零。呼叫時須持有 DAY_LOCK。 */
    private void rollDayIfNeeded() {
        String today = today();
        if (today.equals(sp.getString(KEY_COUNT_DAY, ""))) return;
        SharedPreferences.Editor ed = sp.edit();
        for (String key : sp.getAll().keySet()) {
            if (key.startsWith(PREFIX_COUNT)) ed.remove(key);
        }
        ed.remove(KEY_TODAY_TOTAL);
        ed.remove(KEY_TODAY_RESISTED);
        ed.putString(KEY_COUNT_DAY, today);
        ed.commit();
    }

    /** 記錄今天打開幾次，回傳加一後的次數；換日自動歸零。 */
    public int incrementToday(String targetKey) {
        if (targetKey == null) return 0;
        synchronized (DAY_LOCK) {
            rollDayIfNeeded();
            int count = sp.getInt(PREFIX_COUNT + targetKey, 0) + 1;
            int total = sp.getInt(KEY_TODAY_TOTAL, 0) + 1;
            sp.edit()
                    .putInt(PREFIX_COUNT + targetKey, count)
                    .putInt(KEY_TODAY_TOTAL, total)
                    .apply();
            return count;
        }
    }

    public int getToday(String targetKey) {
        if (targetKey == null) return 0;
        synchronized (DAY_LOCK) {
            return isToday() ? sp.getInt(PREFIX_COUNT + targetKey, 0) : 0;
        }
    }

    /** 今天總共跳出幾次喘口氣畫面（所有目標加總）。 */
    public int getTodayTotal() {
        synchronized (DAY_LOCK) {
            return isToday() ? sp.getInt(KEY_TODAY_TOTAL, 0) : 0;
        }
    }

    /** 今天選擇「算了」幾次。 */
    public int getTodayResisted() {
        synchronized (DAY_LOCK) {
            return isToday() ? sp.getInt(KEY_TODAY_RESISTED, 0) : 0;
        }
    }

    public void addResisted() {
        synchronized (DAY_LOCK) {
            rollDayIfNeeded();
            int n = sp.getInt(KEY_TODAY_RESISTED, 0) + 1;
            sp.edit().putInt(KEY_TODAY_RESISTED, n).apply();
        }
    }
}
