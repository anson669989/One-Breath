package com.onebreath.app.data;

import java.util.LinkedHashSet;
import java.util.Set;

/** 一個群組：裡面的 App（和自訂網站）共用同一套提醒方式。 */
public final class Group {
    public String id;                    // UUID string, set by GroupStore.newGroup
    public String name;
    public boolean enabled = true;       // whole group on/off
    public boolean pauseOnOpen = true;   // 開啟前喘口氣
    public boolean remindInUse = true;   // 使用中提醒
    public boolean blockWeb = true;      // 連同對應網站一起提醒
    public boolean customTiming = false; // false = use global timings from Prefs
    public int delaySeconds;             // 0..30  wait on the open-pause screen
    public int remindMinutes;            // 1..60  in-use reminder interval
    public int remindDelaySeconds;       // 0..30  wait on the in-use reminder screen
    public final Set<String> apps = new LinkedHashSet<>();  // package names
    public final Set<String> sites = new LinkedHashSet<>(); // custom domains, normalised
}
