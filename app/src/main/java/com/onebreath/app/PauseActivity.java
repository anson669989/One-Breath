package com.onebreath.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Random;

/**
 * 深呼吸畫面：只有一個緩慢縮放的圓圈。
 * 等待時間到了，「算了」下方才會出現「我確定」的文字按鈕，永遠不會把你鎖在外面。
 */
public class PauseActivity extends Activity {

    public static final String EXTRA_PKG = "pkg";

    private static final String[] PROMPTS = {
            "你打開它是為了什麼？",
            "這件事現在一定要做嗎？",
            "滑完之後，你會覺得比較好嗎？",
            "先喝口水，再決定。",
            "真的需要，還是只是習慣？",
            "等一下想做的事，做了嗎？"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private Prefs prefs;
    private String pkg;
    private ValueAnimator breath;

    private ImageView appIcon;
    private TextView countText, promptText;
    private Button continueBtn, cancelBtn;
    private View circle;

    private final Runnable reveal = () -> continueBtn.setVisibility(View.VISIBLE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pause);
        prefs = new Prefs(this);

        appIcon = findViewById(R.id.appIcon);
        countText = findViewById(R.id.countText);
        promptText = findViewById(R.id.promptText);
        continueBtn = findViewById(R.id.continueBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        circle = findViewById(R.id.circle);

        continueBtn.setOnClickListener(v -> openAnyway());
        cancelBtn.setOnClickListener(v -> giveUp());

        setup(getIntent().getStringExtra(EXTRA_PKG));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String newPkg = intent.getStringExtra(EXTRA_PKG);
        // 同一個 App 又觸發一次就忽略，避免重複計數
        if (newPkg != null && !newPkg.equals(pkg)) {
            setIntent(intent);
            setup(newPkg);
        }
    }

    private void setup(String targetPkg) {
        if (targetPkg == null) {
            finish();
            return;
        }
        pkg = targetPkg;

        PackageManager pm = getPackageManager();
        String label = pkg;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = pm.getApplicationLabel(ai).toString();
            appIcon.setImageDrawable(pm.getApplicationIcon(ai));
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        int count = prefs.incrementToday(pkg);
        countText.setText("這是你今天第 " + count + " 次打開「" + label + "」");
        promptText.setText(PROMPTS[random.nextInt(PROMPTS.length)]);
        continueBtn.setText("我確定，繼續開「" + label + "」");
        continueBtn.setVisibility(View.INVISIBLE);

        startBreathing();
        // 等待時間照樣計算，只是不顯示秒數
        handler.removeCallbacks(reveal);
        handler.postDelayed(reveal, prefs.getDelaySeconds() * 1000L);
    }

    private void startBreathing() {
        if (breath != null) breath.cancel();
        breath = ValueAnimator.ofFloat(0.55f, 1f);
        breath.setDuration(4000);
        breath.setRepeatMode(ValueAnimator.REVERSE);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.setInterpolator(new AccelerateDecelerateInterpolator());
        breath.addUpdateListener(a -> {
            float s = (float) a.getAnimatedValue();
            circle.setScaleX(s);
            circle.setScaleY(s);
        });
        breath.start();
    }

    private void openAnyway() {
        prefs.startGrace(pkg);
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launch);
            } catch (Exception ignored) {
            }
        }
        finish();
    }

    private void giveUp() {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    @Override
    public void onBackPressed() {
        giveUp();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 按 Home 鍵離開就收掉，下次打開再重新提醒
        if (!isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(reveal);
        if (breath != null) breath.cancel();
        super.onDestroy();
    }
}
