package com.onebreath.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.onebreath.app.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 群組的存放處：整個 App 共用一份，記憶體快取 + SharedPreferences（JSON）。
 * 服務每收到一次事件就會查詢，所以查詢都走事先建好的對照表。
 */
public final class GroupStore {

    private static final String KEY_GROUPS = "groups_v2";
    private static final String KEY_LEGACY_APPS = "apps";

    private static GroupStore instance;

    public static GroupStore get(Context c) {
        synchronized (GroupStore.class) {
            if (instance == null) {
                Context app = c.getApplicationContext();
                instance = new GroupStore(app != null ? app : c);
            }
            return instance;
        }
    }

    /** 某個網域屬於哪個群組，以及它的代表網域（twitter.com → x.com）。 */
    private static final class SiteEntry {
        final Group group;
        final String canonical;

        SiteEntry(Group group, String canonical) {
            this.group = group;
            this.canonical = canonical;
        }
    }

    private final Context app;
    private final SharedPreferences sp;
    private final Prefs prefs;
    private final List<Group> groups = new ArrayList<>();

    // 每次存檔/刪除都重建
    private final Map<String, Group> groupOfAnyApp = new HashMap<>();     // 不論是否啟用
    private final Map<String, Group> groupOfWatchedApp = new HashMap<>(); // 只有啟用的群組
    private final Map<String, SiteEntry> siteOfDomain = new HashMap<>();
    private final Map<String, Group> groupOfCanonical = new HashMap<>();
    private int watchedCount;

    private GroupStore(Context app) {
        this.app = app;
        prefs = new Prefs(app);
        sp = prefs.raw();
        load();
    }

    // ------------------------------------------------------------------ public API

    public synchronized List<Group> getGroups() {
        List<Group> out = new ArrayList<>(groups.size());
        for (Group g : groups) out.add(copyOf(g));
        return out;
    }

    public synchronized Group find(String groupId) {
        Group g = findInternal(groupId);
        return g != null ? copyOf(g) : null;
    }

    /** 還沒存檔；時間預先填入目前的全域設定。 */
    public Group newGroup(String name) {
        Group g = new Group();
        g.id = UUID.randomUUID().toString();
        g.name = name;
        g.delaySeconds = prefs.getDelaySeconds();
        g.remindMinutes = prefs.getRemindMinutes();
        g.remindDelaySeconds = prefs.getRemindDelaySeconds();
        return g;
    }

    /** 新增或依 id 取代；g.apps 會從其他群組移除（一個 App 只能在一個群組）。 */
    public synchronized void save(Group g) {
        if (g == null) return;
        Group copy = copyOf(g);
        if (copy.id == null || copy.id.isEmpty()) copy.id = UUID.randomUUID().toString();
        if (copy.name == null) copy.name = "";
        copy.delaySeconds = Prefs.clamp(copy.delaySeconds, 0, 30);
        copy.remindMinutes = Prefs.clamp(copy.remindMinutes, 1, 60);
        copy.remindDelaySeconds = Prefs.clamp(copy.remindDelaySeconds, 0, 30);

        copy.apps.remove(null);
        copy.apps.remove("");
        Set<String> rawSites = new java.util.LinkedHashSet<>(copy.sites);
        copy.sites.clear();
        for (String s : rawSites) {
            String n = SiteCatalog.normalize(s);
            if (n != null) copy.sites.add(n);
        }

        for (Group other : groups) {
            if (!other.id.equals(copy.id)) other.apps.removeAll(copy.apps);
        }
        boolean replaced = false;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).id.equals(copy.id)) {
                groups.set(i, copy);
                replaced = true;
                break;
            }
        }
        if (!replaced) groups.add(copy);
        persist(null);
        rebuild();
    }

    public synchronized void delete(String groupId) {
        if (groupId == null) return;
        Iterator<Group> it = groups.iterator();
        boolean removed = false;
        while (it.hasNext()) {
            if (groupId.equals(it.next().id)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            persist(null);
            rebuild();
        }
    }

    /** 不論群組是否啟用；不在任何群組則為 null。 */
    public synchronized String groupIdOfApp(String pkg) {
        if (pkg == null) return null;
        Group g = groupOfAnyApp.get(pkg);
        return g != null ? g.id : null;
    }

    /** 啟用中群組裡的 App 數量。 */
    public synchronized int watchedAppCount() {
        return watchedCount;
    }

    public synchronized Rule ruleForApp(String pkg) {
        if (pkg == null) return null;
        Group g = groupOfWatchedApp.get(pkg);
        return g != null ? resolve(g) : null;
    }

    /**
     * host = 正在瀏覽的網頁主機（已 normalize）。回傳對上的網域，沒有則 null。
     * 同一個 App 的多個網域會回傳同一個代表網域（twitter.com → x.com），避免換網域時重複跳出。
     */
    public synchronized String matchSite(String host) {
        if (host == null || siteOfDomain.isEmpty()) return null;
        String h = host.toLowerCase(Locale.ROOT);
        while (true) {
            SiteEntry e = siteOfDomain.get(h);
            if (e != null) return e.canonical;
            int dot = h.indexOf('.');
            if (dot < 0) return null;
            h = h.substring(dot + 1);
        }
    }

    public synchronized Rule ruleForSite(String domain) {
        if (domain == null) return null;
        String d = domain.toLowerCase(Locale.ROOT);
        if (d.startsWith("web:")) d = d.substring(4);
        Group g = groupOfCanonical.get(d);
        if (g == null) {
            SiteEntry e = siteOfDomain.get(d);
            if (e != null) g = e.group;
        }
        return g != null ? resolve(g) : null;
    }

    public Rule ruleFor(String targetKey) {
        if (targetKey == null) return null;
        if (targetKey.startsWith("web:")) return ruleForSite(targetKey.substring(4));
        return ruleForApp(targetKey);
    }

    /** 是否至少有一個啟用的群組可能對上網站（給服務快速略過瀏覽器事件用）。 */
    public synchronized boolean hasSiteRules() {
        return !siteOfDomain.isEmpty();
    }

    // ------------------------------------------------------------------ internals

    private Group findInternal(String id) {
        if (id == null) return null;
        for (Group g : groups) if (id.equals(g.id)) return g;
        return null;
    }

    /** 群組自訂時間就用群組的，否則每次都重新讀全域設定。 */
    private Rule resolve(Group g) {
        int delay, remind, remindDelay;
        if (g.customTiming) {
            delay = Prefs.clamp(g.delaySeconds, 0, 30);
            remind = Prefs.clamp(g.remindMinutes, 1, 60);
            remindDelay = Prefs.clamp(g.remindDelaySeconds, 0, 30);
        } else {
            delay = prefs.getDelaySeconds();
            remind = prefs.getRemindMinutes();
            remindDelay = prefs.getRemindDelaySeconds();
        }
        return new Rule(g.id, g.name, g.pauseOnOpen, g.remindInUse, delay, remind, remindDelay);
    }

    private void rebuild() {
        groupOfAnyApp.clear();
        groupOfWatchedApp.clear();
        siteOfDomain.clear();
        groupOfCanonical.clear();
        for (Group g : groups) {
            for (String pkg : g.apps) {
                if (!groupOfAnyApp.containsKey(pkg)) groupOfAnyApp.put(pkg, g);
                if (g.enabled && !groupOfWatchedApp.containsKey(pkg)) groupOfWatchedApp.put(pkg, g);
            }
        }
        watchedCount = groupOfWatchedApp.size();

        // 自訂網站優先，再來是內建對照表
        for (Group g : groups) {
            if (!g.enabled) continue;
            for (String d : g.sites) addSite(d, d, g);
        }
        for (Group g : groups) {
            if (!g.enabled || !g.blockWeb) continue;
            for (String pkg : g.apps) {
                if (groupOfWatchedApp.get(pkg) != g) continue;
                Set<String> domains = SiteCatalog.domainsFor(pkg);
                String canonical = null;
                for (String d : domains) {
                    if (canonical == null) canonical = d;
                    addSite(d, canonical, g);
                }
            }
        }
    }

    private void addSite(String domain, String canonical, Group g) {
        if (domain == null || domain.isEmpty()) return;
        if (!siteOfDomain.containsKey(domain)) siteOfDomain.put(domain, new SiteEntry(g, canonical));
        if (!groupOfCanonical.containsKey(canonical)) groupOfCanonical.put(canonical, g);
    }

    private void load() {
        groups.clear();
        String json = sp.getString(KEY_GROUPS, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    Group g = fromJson(o);
                    if (g.id == null || g.id.isEmpty() || findInternal(g.id) != null) continue;
                    for (Group other : groups) g.apps.removeAll(other.apps);
                    groups.add(g);
                }
            } catch (JSONException ignored) {
                // 資料壞掉就當作沒有群組，不讓 App 當掉
            }
        }

        // 舊版（v1）只有一個 App 清單：搬進「我的 App」群組。
        // 名稱用搬移當下的介面語言，之後就是使用者自己的資料，不會再跟著語言改。
        Set<String> legacy = sp.getStringSet(KEY_LEGACY_APPS, null);
        if (groups.isEmpty() && legacy != null && !legacy.isEmpty()) {
            Group g = newGroup(AppLocale.wrap(app).getString(R.string.legacy_group_name));
            for (String pkg : new java.util.TreeSet<>(legacy)) {
                if (pkg != null && !pkg.isEmpty()) g.apps.add(pkg);
            }
            groups.add(g);
            persist(KEY_LEGACY_APPS);
        }
        rebuild();
    }

    private void persist(String alsoRemoveKey) {
        JSONArray arr = new JSONArray();
        for (Group g : groups) {
            try {
                arr.put(toJson(g));
            } catch (JSONException ignored) {
            }
        }
        SharedPreferences.Editor ed = sp.edit().putString(KEY_GROUPS, arr.toString());
        if (alsoRemoveKey != null) ed.remove(alsoRemoveKey);
        ed.apply();
    }

    private static JSONObject toJson(Group g) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", g.id);
        o.put("name", g.name != null ? g.name : "");
        o.put("enabled", g.enabled);
        o.put("pauseOnOpen", g.pauseOnOpen);
        o.put("remindInUse", g.remindInUse);
        o.put("blockWeb", g.blockWeb);
        o.put("customTiming", g.customTiming);
        o.put("delaySeconds", g.delaySeconds);
        o.put("remindMinutes", g.remindMinutes);
        o.put("remindDelaySeconds", g.remindDelaySeconds);
        JSONArray apps = new JSONArray();
        for (String a : g.apps) apps.put(a);
        o.put("apps", apps);
        JSONArray sites = new JSONArray();
        for (String s : g.sites) sites.put(s);
        o.put("sites", sites);
        return o;
    }

    private Group fromJson(JSONObject o) {
        Group g = new Group();
        g.id = o.optString("id", null);
        g.name = o.optString("name", "");
        g.enabled = o.optBoolean("enabled", true);
        g.pauseOnOpen = o.optBoolean("pauseOnOpen", true);
        g.remindInUse = o.optBoolean("remindInUse", true);
        g.blockWeb = o.optBoolean("blockWeb", true);
        g.customTiming = o.optBoolean("customTiming", false);
        g.delaySeconds = Prefs.clamp(o.optInt("delaySeconds", prefs.getDelaySeconds()), 0, 30);
        g.remindMinutes = Prefs.clamp(o.optInt("remindMinutes", prefs.getRemindMinutes()), 1, 60);
        g.remindDelaySeconds = Prefs.clamp(o.optInt("remindDelaySeconds", prefs.getRemindDelaySeconds()), 0, 30);
        JSONArray apps = o.optJSONArray("apps");
        if (apps != null) {
            for (int i = 0; i < apps.length(); i++) {
                String a = apps.optString(i, null);
                if (a != null && !a.isEmpty()) g.apps.add(a);
            }
        }
        JSONArray sites = o.optJSONArray("sites");
        if (sites != null) {
            for (int i = 0; i < sites.length(); i++) {
                String n = SiteCatalog.normalize(sites.optString(i, null));
                if (n != null) g.sites.add(n);
            }
        }
        return g;
    }

    private static Group copyOf(Group src) {
        Group g = new Group();
        g.id = src.id;
        g.name = src.name;
        g.enabled = src.enabled;
        g.pauseOnOpen = src.pauseOnOpen;
        g.remindInUse = src.remindInUse;
        g.blockWeb = src.blockWeb;
        g.customTiming = src.customTiming;
        g.delaySeconds = src.delaySeconds;
        g.remindMinutes = src.remindMinutes;
        g.remindDelaySeconds = src.remindDelaySeconds;
        g.apps.addAll(src.apps);
        g.sites.addAll(src.sites);
        return g;
    }
}
