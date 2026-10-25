package com.onebreath.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 所有設定都存在手機本機，不會上傳。 */
public class Prefs {
    private static final String KEY_APPS = "apps";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DELAY = "delay_seconds";
    private static final String KEY_GRACE = "grace_minutes";
    private static final String KEY_COUNT_DAY = "count_day";
    private static final String PREFIX_GRACE = "grace_until_";
    private static final String PREFIX_COUNT = "cnt_";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public Set<String> getApps() {
        return new HashSet<>(sp.getStringSet(KEY_APPS, Collections.emptySet()));
    }

    public void setApps(Set<String> apps) {
        sp.edit().putStringSet(KEY_APPS, new HashSet<>(apps)).apply();
    }

    public boolean isEnabled() {
        return sp.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** 倒數幾秒才能按「繼續」，0 = 不用等。 */
    public int getDelaySeconds() {
        return sp.getInt(KEY_DELAY, 5);
    }

    public void setDelaySeconds(int seconds) {
        sp.edit().putInt(KEY_DELAY, seconds).apply();
    }

    /** 按「繼續」後幾分鐘內不再跳提醒。 */
    public int getGraceMinutes() {
        return sp.getInt(KEY_GRACE, 5);
    }

    public void setGraceMinutes(int minutes) {
        sp.edit().putInt(KEY_GRACE, minutes).apply();
    }

    public void startGrace(String pkg) {
        long until = System.currentTimeMillis() + getGraceMinutes() * 60_000L;
        sp.edit().putLong(PREFIX_GRACE + pkg, until).apply();
    }

    public boolean inGrace(String pkg) {
        return System.currentTimeMillis() < sp.getLong(PREFIX_GRACE + pkg, 0);
    }

    /** 記錄今天打開幾次，回傳加一後的次數；換日自動歸零。 */
    public int incrementToday(String pkg) {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        SharedPreferences.Editor ed = sp.edit();
        if (!today.equals(sp.getString(KEY_COUNT_DAY, ""))) {
            for (String key : sp.getAll().keySet()) {
                if (key.startsWith(PREFIX_COUNT)) ed.remove(key);
            }
            ed.putString(KEY_COUNT_DAY, today);
            ed.commit();
            ed = sp.edit();
        }
        int count = sp.getInt(PREFIX_COUNT + pkg, 0) + 1;
        ed.putInt(PREFIX_COUNT + pkg, count).apply();
        return count;
    }
}
