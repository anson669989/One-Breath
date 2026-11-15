package com.onebreath.app.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 內建對照表：常見 App 的套件名稱 → 它的網站網域。 */
public final class SiteCatalog {

    private static final Map<String, Set<String>> TABLE = new HashMap<>();

    static {
        // 每個 App 一行；第一個網域是「代表網域」，同一個 App 的其他網域都算成它
        add("com.twitter.android", "x.com", "twitter.com");
        add("com.instagram.android", "instagram.com");
        add("com.instagram.barcelona", "threads.net", "threads.com");
        add("com.facebook.katana", "facebook.com");
        add("com.facebook.lite", "facebook.com");
        add("com.facebook.orca", "messenger.com");
        add("com.google.android.youtube", "youtube.com");
        add("com.google.android.apps.youtube.music", "music.youtube.com");
        add("com.zhiliaoapp.musically", "tiktok.com");
        add("com.ss.android.ugc.trill", "tiktok.com");
        add("com.ss.android.ugc.aweme", "douyin.com");
        add("com.reddit.frontpage", "reddit.com");
        add("tv.twitch.android.app", "twitch.tv");
        add("com.netflix.mediaclient", "netflix.com");
        add("com.discord", "discord.com");
        add("com.pinterest", "pinterest.com");
        add("com.snapchat.android", "snapchat.com");
        add("com.linkedin.android", "linkedin.com");
        add("com.tumblr", "tumblr.com");
        add("tv.danmaku.bili", "bilibili.com");
        add("com.bilibili.app.in", "bilibili.tv", "bilibili.com");
        add("com.sparkslab.dcardreader", "dcard.tw");
        add("org.telegram.messenger", "web.telegram.org");
        add("com.whatsapp", "web.whatsapp.com");
        add("com.disney.disneyplus", "disneyplus.com");
        add("com.shopee.tw", "shopee.tw");
        add("com.xingin.xhs", "xiaohongshu.com");
        add("com.sina.weibo", "weibo.com", "weibo.cn");
        add("com.ninegag.android.app", "9gag.com");
        add("com.quora.android", "quora.com");
    }

    private SiteCatalog() {
    }

    private static void add(String pkg, String... domains) {
        Set<String> set = new LinkedHashSet<>();
        Collections.addAll(set, domains);
        TABLE.put(pkg, Collections.unmodifiableSet(set));
    }

    /** 內建的 App → 網域表；不認得就回傳空集合（不可修改）。 */
    public static Set<String> domainsFor(String pkg) {
        if (pkg == null) return Collections.emptySet();
        Set<String> s = TABLE.get(pkg);
        return s != null ? s : Collections.<String>emptySet();
    }

    /**
     * 把網址列看到的文字變成主機名稱："https://www.X.com/home?a" → "x.com"。
     * 只去掉開頭的 "www."（"m." 等子網域靠 hostMatches 的後綴比對處理）。
     * 不是網址（搜尋文字、沒有點、有空白…）就回傳 null。
     */
    public static String normalize(String urlOrHost) {
        if (urlOrHost == null) return null;
        String s = urlOrHost.trim();
        if (s.isEmpty()) return null;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return null;
        }
        s = s.toLowerCase(Locale.ROOT);

        // 去掉 scheme（https://、http:// …）
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            for (int i = 0; i < scheme; i++) {
                char c = s.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.')) {
                    return null;
                }
            }
            s = s.substring(scheme + 3);
        }

        // 只留主機那一段
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#' || c == '\\') {
                end = i;
                break;
            }
        }
        s = s.substring(0, end);

        int at = s.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        if (s.startsWith("[")) return null; // IPv6，不會對應到任何網站

        int colon = s.indexOf(':');
        if (colon >= 0) {
            // "host:443" 留下 host；"about:blank"、"javascript:..." 這類不是主機
            String port = s.substring(colon + 1);
            for (int i = 0; i < port.length(); i++) {
                char c = port.charAt(i);
                if (c < '0' || c > '9') return null;
            }
            s = s.substring(0, colon);
        }

        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.startsWith("www.")) s = s.substring(4);

        if (s.isEmpty() || s.indexOf('.') < 0) return null;
        if (s.startsWith(".") || s.contains("..")) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.'
                    || c == '_' || (c > 127 && Character.isLetterOrDigit(c));
            if (!ok) return null;
        }
        return s;
    }

    /** host 與 domain 相同，或 host 是 domain 的子網域。 */
    public static boolean hostMatches(String host, String domain) {
        if (host == null || domain == null || domain.isEmpty()) return false;
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
