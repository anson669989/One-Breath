package com.onebreath.app.service;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.onebreath.app.data.GroupStore;
import com.onebreath.app.data.Prefs;
import com.onebreath.app.data.Rule;
import com.onebreath.app.ui.PauseActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * 「使用中提醒」的計時：只累計目標在最前面的時間，到了就再跳一次畫面。
 * 狀態只放在記憶體裡（整個 process 共用）。服務沒在跑時，靜態方法仍可安全呼叫。
 */
public final class Sessions {

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static final Map<String, Session> SESSIONS = new HashMap<>();

    /** 目前連著的無障礙服務；計時到了用它來開畫面。null = 服務沒在跑。 */
    private static Context service;

    private static final class Session {
        final String key;
        String hostPkg;
        long usedMs;              // 已累計的前景時間（不含正在跑的這段）
        long runningSince = -1;   // elapsedRealtime；-1 = 目前不在前景
        long leftAt = -1;         // elapsedRealtime；-1 = 還沒離開過
        long intervalStartMs;     // 這一輪提醒從累計到幾毫秒開始算
        Runnable timer;

        Session(String key, long now) {
            this.key = key;
            this.leftAt = now;    // 還沒進前景前，也算「離開中」，逾時就作廢
        }

        long used(long now) {
            return usedMs + (runningSince >= 0 ? now - runningSince : 0);
        }
    }

    private Sessions() {
    }

    // ------------------------------------------------------------------ 給 PauseActivity

    /** 使用者按了「繼續」。mode 是 PauseActivity.MODE_OPEN 或 MODE_REMIND。 */
    public static void userContinued(Context c, String targetKey, String mode) {
        if (targetKey == null) return;
        Prefs prefs = null;
        try {
            prefs = new Prefs(c);
            prefs.startGrace(targetKey);
        } catch (Exception ignored) {
        }
        try {
            synchronized (LOCK) {
                long now = SystemClock.elapsedRealtime();
                Session s = SESSIONS.get(targetKey);
                if (PauseActivity.MODE_REMIND.equals(mode)) {
                    if (s == null) {
                        s = new Session(targetKey, now);
                        SESSIONS.put(targetKey, s);
                    }
                    // 重新倒數一輪，但總使用時間保留
                    s.intervalStartMs = s.used(now);
                    if (s.runningSince >= 0) {
                        Rule r = GroupStore.get(c).ruleFor(targetKey);
                        if (r != null && r.remindInUse) schedule(s, r.remindMinutes, now);
                    }
                } else {
                    if (s == null || (prefs != null && !isAlive(s, now, prefs))) {
                        if (s != null) cancel(s);
                        SESSIONS.put(targetKey, new Session(targetKey, now));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 使用者放棄了（按鈕、返回鍵或 Home）。清掉寬限期與計時，記一次「忍住」。 */
    public static void userQuit(Context c, String targetKey) {
        if (targetKey == null) return;
        try {
            Prefs prefs = new Prefs(c);
            prefs.clearGrace(targetKey);
            prefs.addResisted();
        } catch (Exception ignored) {
        }
        synchronized (LOCK) {
            Session s = SESSIONS.remove(targetKey);
            if (s != null) cancel(s);
        }
    }

    // ------------------------------------------------------------------ 給 PauseService

    static void attach(Context svc) {
        synchronized (LOCK) {
            service = svc;
        }
    }

    /** 服務被關掉：停掉所有計時，正在跑的時間先記起來。 */
    static void detach() {
        synchronized (LOCK) {
            service = null;
            long now = SystemClock.elapsedRealtime();
            for (Session s : SESSIONS.values()) stop(s, now);
        }
    }

    /** 目標來到最前面（而且沒有被喘口氣畫面擋著）。 */
    static void enter(Context c, String targetKey, String hostPkg) {
        if (targetKey == null) return;
        try {
            Prefs prefs = new Prefs(c);
            if (!prefs.isEnabled()) return;
            Rule r = GroupStore.get(c).ruleFor(targetKey);
            if (r == null || !r.remindInUse) return;
            synchronized (LOCK) {
                long now = SystemClock.elapsedRealtime();
                Session s = SESSIONS.get(targetKey);
                if (s != null && !isAlive(s, now, prefs)) {
                    cancel(s);
                    s = null;
                }
                if (s == null) {
                    s = new Session(targetKey, now);
                    SESSIONS.put(targetKey, s);
                }
                s.hostPkg = hostPkg;
                if (s.runningSince < 0) s.runningSince = now;
                s.leftAt = -1;
                schedule(s, r.remindMinutes, now);
            }
        } catch (Exception ignored) {
        }
    }

    /** 目標離開最前面（換 App、換網頁、關螢幕、跳出喘口氣畫面…）。 */
    static void leave(String targetKey) {
        if (targetKey == null) return;
        synchronized (LOCK) {
            Session s = SESSIONS.get(targetKey);
            if (s != null) stop(s, SystemClock.elapsedRealtime());
        }
    }

    // ------------------------------------------------------------------ internals

    /** 離開沒超過寬限分鐘數，就還是同一段使用。 */
    private static boolean isAlive(Session s, long now, Prefs prefs) {
        if (s.runningSince >= 0 || s.leftAt < 0) return true;
        return now - s.leftAt <= prefs.getGraceMinutes() * 60_000L;
    }

    private static void stop(Session s, long now) {
        cancel(s);
        if (s.runningSince >= 0) {
            s.usedMs += Math.max(0, now - s.runningSince);
            s.runningSince = -1;
            s.leftAt = now;
        }
    }

    private static void cancel(Session s) {
        if (s.timer != null) {
            HANDLER.removeCallbacks(s.timer);
            s.timer = null;
        }
    }

    private static void schedule(Session s, int remindMinutes, long now) {
        cancel(s);
        long due = s.intervalStartMs + remindMinutes * 60_000L;
        long wait = Math.max(500L, due - s.used(now));
        final String key = s.key;
        Runnable r = () -> fire(key);
        s.timer = r;
        HANDLER.postDelayed(r, wait);
    }

    private static void fire(String key) {
        Intent intent;
        Context ctx;
        try {
            synchronized (LOCK) {
                Session s = SESSIONS.get(key);
                if (s == null) return;
                s.timer = null;
                ctx = service;
                if (ctx == null || s.runningSince < 0) return;
                if (!new Prefs(ctx).isEnabled()) return;
                Rule r = GroupStore.get(ctx).ruleFor(key);
                if (r == null || !r.remindInUse) return;
                long now = SystemClock.elapsedRealtime();
                long used = s.used(now);
                // 先把下一輪的起點設好：萬一畫面沒跳出來，也不會一直重複觸發
                s.intervalStartMs = used;
                int minutes = (int) Math.max(1, Math.round(used / 60_000.0));
                intent = PauseActivity.intent(ctx, key, PauseActivity.MODE_REMIND, s.hostPkg, minutes);
                schedule(s, r.remindMinutes, now);
            }
            ctx.startActivity(intent);
        } catch (Exception ignored) {
            // 跳不出畫面就安靜放行，絕不卡住使用者
        }
    }
}
