package com.onebreath.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.onebreath.app.R;
import com.onebreath.app.data.GroupStore;
import com.onebreath.app.data.Prefs;
import com.onebreath.app.data.Rule;
import com.onebreath.app.service.Sessions;

import java.util.Random;

/**
 * 喘口氣畫面。兩種模式：
 * <ul>
 *   <li>{@link #MODE_OPEN}：打開被監控的 App / 網站之前。</li>
 *   <li>{@link #MODE_REMIND}：已經用了一段時間，問一聲還要不要繼續（暖色調）。</li>
 * </ul>
 * 等待時間一到，「繼續」的文字按鈕才會淡入；永遠不會把人鎖在外面。
 * 倒數由 elapsedRealtime 計時，不依賴動畫，所以系統關閉動畫時一樣準時出現。
 */
public class PauseActivity extends Activity {

    public static final String MODE_OPEN = "open";
    public static final String MODE_REMIND = "remind";

    private static final String EXTRA_KEY = "com.onebreath.app.extra.TARGET_KEY";
    private static final String EXTRA_MODE = "com.onebreath.app.extra.MODE";
    private static final String EXTRA_HOST = "com.onebreath.app.extra.HOST_PKG";
    private static final String EXTRA_USED = "com.onebreath.app.extra.USED_MINUTES";

    private static final String WEB_PREFIX = "web:";
    private static final long TICK_MS = 200L;

    private static final String[] PROMPTS = {
            "你打開它是為了什麼？",
            "這件事現在一定要做嗎？",
            "滑完之後，你會覺得比較好嗎？",
            "先喝口水，再決定。",
            "真的需要，還是只是習慣？",
            "等一下想做的事，做了嗎？",
            "此刻的你，其實想要什麼？",
            "是無聊，還是累了？",
            "五分鐘後的你，會感謝這個決定嗎？",
            "先看看窗外，再決定。",
            "有沒有一件更想做的小事？",
            "你在找的東西，在這裡嗎？"
    };

    private static final String[] REMIND_PROMPTS = {
            "還要繼續嗎？",
            "原本想做的事，做完了嗎？",
            "現在停下來，剛剛好。",
            "眼睛和肩膀，還好嗎？",
            "這段時間，花得值得嗎？",
            "起來走走，好不好？",
            "你還記得打開它的原因嗎？",
            "再滑下去，會更開心嗎？"
    };

    private static int lastPrompt = -1;
    private static int lastRemindPrompt = -1;

    /**
     * hostPkg：網站目標時是瀏覽器的套件名稱，否則 null。usedMinutes：只用於 MODE_REMIND。
     * 回傳的 Intent 已經帶 FLAG_ACTIVITY_NEW_TASK。
     */
    public static Intent intent(Context c, String targetKey, String mode, String hostPkg, int usedMinutes) {
        Intent i = new Intent(c, PauseActivity.class);
        i.putExtra(EXTRA_KEY, targetKey);
        i.putExtra(EXTRA_MODE, MODE_REMIND.equals(mode) ? MODE_REMIND : MODE_OPEN);
        if (hostPkg != null) i.putExtra(EXTRA_HOST, hostPkg);
        i.putExtra(EXTRA_USED, usedMinutes);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private Prefs prefs;

    // 目前顯示中的目標
    private String key;
    private String mode = MODE_OPEN;
    private boolean isWeb;
    private boolean decided;      // 已呼叫過 userContinued / userQuit，避免重複
    private boolean revealed;
    private long deadline;        // elapsedRealtime
    private long setupAt;         // elapsedRealtime
    private boolean animationsOn = true;

    private ScrollView scroll;
    private View caption;
    private ImageView icon;
    private TextView nameText, eyebrowText, questionText, statText, secondaryBtn;
    private Button primaryBtn;
    private PauseOrbView orb;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            if (now >= deadline) {
                reveal();
                return;
            }
            handler.postDelayed(this, Math.min(TICK_MS, Math.max(16L, deadline - now)));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();
        setContentView(R.layout.activity_pause);
        prefs = new Prefs(this);

        scroll = findViewById(R.id.pauseScroll);
        caption = findViewById(R.id.pauseCaption);
        icon = findViewById(R.id.pauseIcon);
        nameText = findViewById(R.id.pauseName);
        orb = findViewById(R.id.pauseOrb);
        eyebrowText = findViewById(R.id.pauseEyebrow);
        questionText = findViewById(R.id.pauseQuestion);
        statText = findViewById(R.id.pauseStat);
        primaryBtn = findViewById(R.id.pausePrimary);
        secondaryBtn = findViewById(R.id.pauseSecondary);

        primaryBtn.setOnClickListener(v -> giveUp(true));
        secondaryBtn.setOnClickListener(v -> {
            if (revealed) continueAnyway();
        });

        Intent in = getIntent();
        if (!setup(in.getStringExtra(EXTRA_KEY), in.getStringExtra(EXTRA_MODE),
                in.getStringExtra(EXTRA_HOST), in.getIntExtra(EXTRA_USED, 0))) {
            decided = true;
            finish();
        }
    }

    /** 背景漸層延伸到狀態列後面；導覽列用漸層最底的顏色。 */
    private void setupWindow() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(getColor(R.color.pause_bg_bottom));
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (decided) return;
        String newKey = intent.getStringExtra(EXTRA_KEY);
        String newMode = MODE_REMIND.equals(intent.getStringExtra(EXTRA_MODE)) ? MODE_REMIND : MODE_OPEN;
        if (newKey == null) return;
        // 同一個目標、同一種模式又觸發一次就忽略，避免重複計數
        if (newKey.equals(key) && newMode.equals(mode)) return;
        setIntent(intent);
        setup(newKey, newMode, intent.getStringExtra(EXTRA_HOST), intent.getIntExtra(EXTRA_USED, 0));
    }

    /** @return false 代表沒有目標可顯示 */
    private boolean setup(String targetKey, String targetMode, String hostPkg, int usedMinutes) {
        if (targetKey == null || targetKey.isEmpty()) return false;
        key = targetKey;
        mode = MODE_REMIND.equals(targetMode) ? MODE_REMIND : MODE_OPEN;
        isWeb = key.startsWith(WEB_PREFIX);
        boolean remind = MODE_REMIND.equals(mode);

        // ---- 名稱與圖示
        String label = bindTarget(hostPkg);

        // ---- 模式配色
        int accent = getColor(remind ? R.color.pause_warm : R.color.pause_accent);
        int accentLight = getColor(remind ? R.color.pause_warm_light : R.color.pause_accent_light);
        orb.setPalette(accent, accentLight, getColor(R.color.pause_text_primary));
        eyebrowText.setTextColor(accentLight);
        primaryBtn.setBackgroundResource(remind ? R.drawable.pause_pill_warm : R.drawable.pause_pill);
        primaryBtn.setTextColor(getColor(remind ? R.color.pause_on_warm : R.color.pause_on_accent));

        // ---- 文字
        if (remind) {
            eyebrowText.setText(usedMinutes > 0
                    ? "你已經用了 " + usedMinutes + " 分鐘"
                    : "你已經用了一段時間");
            questionText.setText(pickRemindPrompt());
            int today = prefs.getToday(key);
            statText.setText(today > 0
                    ? "今天打開「" + label + "」" + today + " 次"
                    : "停一下，也是一種照顧自己");
            primaryBtn.setText("夠了，先離開");
            secondaryBtn.setText("再用一下");
        } else {
            int count = prefs.incrementToday(key);
            eyebrowText.setText("先停一下，深呼吸");
            questionText.setText(pickOpenPrompt());
            statText.setText("今天第 " + count + " 次打開「" + label + "」");
            primaryBtn.setText("算了，不開了");
            secondaryBtn.setText("我確定，繼續開「" + label + "」");
        }

        // ---- 等待時間
        int waitSec = waitSeconds(remind);
        long now = SystemClock.elapsedRealtime();
        setupAt = now;
        deadline = now + waitSec * 1000L;
        animationsOn = PauseOrbView.systemAnimationsEnabled();

        revealed = false;
        secondaryBtn.animate().cancel();
        secondaryBtn.setVisibility(View.INVISIBLE);
        secondaryBtn.setAlpha(0f);
        secondaryBtn.setEnabled(false);

        orb.start(animationsOn);

        scroll.scrollTo(0, 0);
        playEntrance();

        handler.removeCallbacks(tick);
        handler.post(tick);
        return true;
    }

    /** 設定頂端的圖示與名稱，回傳顯示用名稱。 */
    private String bindTarget(String hostPkg) {
        PackageManager pm = getPackageManager();
        String label;
        Drawable d = null;
        if (isWeb) {
            label = key.substring(WEB_PREFIX.length());
            if (label.isEmpty()) label = "這個網站";
            if (hostPkg != null) {
                try {
                    d = pm.getApplicationIcon(hostPkg);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        } else {
            label = key;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(key, 0);
                CharSequence l = pm.getApplicationLabel(ai);
                if (l != null && l.length() > 0) label = l.toString();
                d = pm.getApplicationIcon(ai);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        if (d != null) {
            icon.setImageDrawable(d);
            icon.setPadding(0, 0, 0, 0);
        } else {
            icon.setImageResource(R.drawable.pause_globe);
            int p = dp(1);
            icon.setPadding(p, p, p, p);
        }
        nameText.setText(label);
        return label;
    }

    private int waitSeconds(boolean remind) {
        int s;
        Rule rule = null;
        try {
            rule = GroupStore.get(this).ruleFor(key);
        } catch (Exception ignored) {
        }
        if (rule != null) {
            s = remind ? rule.remindDelaySeconds : rule.delaySeconds;
        } else {
            s = remind ? prefs.getRemindDelaySeconds() : prefs.getDelaySeconds();
        }
        return Math.max(0, Math.min(60, s));
    }

    private String pickOpenPrompt() {
        int i = random.nextInt(PROMPTS.length);
        if (i == lastPrompt) i = (i + 1) % PROMPTS.length;
        lastPrompt = i;
        return PROMPTS[i];
    }

    private String pickRemindPrompt() {
        int i = random.nextInt(REMIND_PROMPTS.length);
        if (i == lastRemindPrompt) i = (i + 1) % REMIND_PROMPTS.length;
        lastRemindPrompt = i;
        return REMIND_PROMPTS[i];
    }

    // ---------------------------------------------------------------- 動畫

    private static final long ENTER_BASE_DELAY = 80L;
    private static final long ENTER_STAGGER = 90L;
    private static final long ENTER_DURATION = 700L;

    /** 內容由下往上淡入，依序錯開。 */
    private void playEntrance() {
        View[] views = {caption, orb, eyebrowText, questionText, statText, primaryBtn};
        float rise = dp(18);
        DecelerateInterpolator ease = new DecelerateInterpolator(1.8f);
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(v == orb ? rise * 0.5f : rise);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(ENTER_BASE_DELAY + ENTER_STAGGER * i)
                    .setDuration(v == orb ? ENTER_DURATION + 300L : ENTER_DURATION)
                    .setInterpolator(ease)
                    .start();
        }
    }

    /** 等待結束：「繼續」文字按鈕緩緩淡入。 */
    private void reveal() {
        if (revealed || decided) return;
        revealed = true;
        handler.removeCallbacks(tick);
        if (!animationsOn) orb.invalidate();
        secondaryBtn.animate().cancel();
        secondaryBtn.setAlpha(0f);
        secondaryBtn.setVisibility(View.VISIBLE);
        secondaryBtn.setEnabled(true);
        // 入場動畫還沒走完（例如等待 0 秒）就等它走完再淡入，不要搶在最前面
        long sinceSetup = SystemClock.elapsedRealtime() - setupAt;
        long entranceEnd = ENTER_BASE_DELAY + ENTER_STAGGER * 6 + 200L;
        long delay = Math.max(0L, entranceEnd - sinceSetup);
        secondaryBtn.animate()
                .alpha(1f)
                .setStartDelay(delay)
                .setDuration(900L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ---------------------------------------------------------------- 決定

    private void continueAnyway() {
        if (decided) return;
        decided = true;
        Sessions.userContinued(this, key, mode);
        if (!isWeb && MODE_OPEN.equals(mode)) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(key);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(launch);
                } catch (Exception ignored) {
                }
            }
        }
        finish();
    }

    private void giveUp(boolean goHome) {
        if (decided) return;
        decided = true;
        if (key != null) Sessions.userQuit(this, key);
        if (goHome) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(home);
            } catch (Exception ignored) {
            }
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        giveUp(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 按 Home 鍵、切走或關螢幕：視同放棄，收掉畫面，下次再重新提醒
        if (!isFinishing()) {
            if (!decided) giveUp(false);
            else finish();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (orb != null) {
            orb.stop();
            orb.animate().cancel();
        }
        View[] views = {caption, eyebrowText, questionText, statText, primaryBtn, secondaryBtn};
        for (View v : views) {
            if (v != null) v.animate().cancel();
        }
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
