package com.onebreath.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 設定頁：開權限、調等待秒數、搜尋並勾選要提醒的 App。 */
public class MainActivity extends Activity {

    private static class AppItem {
        String pkg;
        String label;
        Drawable icon;
        boolean checked;
        View row;
    }

    private Prefs prefs;
    private final List<AppItem> apps = new ArrayList<>();

    private TextView statusText, samsungHint, delayLabel, graceLabel, emptyText;
    private Button accessBtn, appInfoBtn;
    private LinearLayout appList;
    private EditText searchBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);

        statusText = findViewById(R.id.statusText);
        samsungHint = findViewById(R.id.samsungHint);
        accessBtn = findViewById(R.id.accessBtn);
        appInfoBtn = findViewById(R.id.appInfoBtn);
        delayLabel = findViewById(R.id.delayLabel);
        graceLabel = findViewById(R.id.graceLabel);
        emptyText = findViewById(R.id.emptyText);
        appList = findViewById(R.id.appList);
        searchBox = findViewById(R.id.searchBox);

        accessBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        appInfoBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        Switch enableSwitch = findViewById(R.id.enableSwitch);
        enableSwitch.setChecked(prefs.isEnabled());
        enableSwitch.setOnCheckedChangeListener((b, on) -> prefs.setEnabled(on));

        SeekBar delaySeek = findViewById(R.id.delaySeek);
        delaySeek.setProgress(prefs.getDelaySeconds());
        updateDelayLabel(prefs.getDelaySeconds());
        delaySeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                prefs.setDelaySeconds(value);
                updateDelayLabel(value);
            }
        });

        SeekBar graceSeek = findViewById(R.id.graceSeek);
        graceSeek.setProgress(prefs.getGraceMinutes() - 1);
        updateGraceLabel(prefs.getGraceMinutes());
        graceSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                prefs.setGraceMinutes(value + 1);
                updateGraceLabel(value + 1);
            }
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateDelayLabel(int s) {
        delayLabel.setText(s == 0 ? "等待：不用等，直接可以選" : "等待 " + s + " 秒後才能選「我確定」");
    }

    private void updateGraceLabel(int m) {
        graceLabel.setText("選「我確定」後 " + m + " 分鐘內不再提醒");
    }

    private void updateStatus() {
        boolean on = isServiceEnabled();
        if (on) {
            statusText.setText("✓ 無障礙權限已開啟，提醒運作中");
            statusText.setBackgroundColor(0xFF12305C);
            statusText.setTextColor(0xFFA9C7FF);
            accessBtn.setText("無障礙設定");
            samsungHint.setVisibility(View.GONE);
            appInfoBtn.setVisibility(View.GONE);
        } else {
            statusText.setText("✗ 還沒開啟無障礙權限，提醒不會出現");
            statusText.setBackgroundColor(0xFF3A1E2A);
            statusText.setTextColor(0xFFF2A7B8);
            accessBtn.setText("① 開啟無障礙權限（找「喘口氣」）");
            samsungHint.setVisibility(View.VISIBLE);
            appInfoBtn.setVisibility(View.VISIBLE);
        }
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, PauseService.class);
        for (String s : enabled.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
            Set<String> selected = prefs.getApps();
            Set<String> seen = new HashSet<>();
            List<AppItem> result = new ArrayList<>();
            for (ResolveInfo ri : infos) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                AppItem item = new AppItem();
                item.pkg = pkg;
                item.label = ri.loadLabel(pm).toString();
                item.icon = ri.loadIcon(pm);
                item.checked = selected.contains(pkg);
                result.add(item);
            }
            Collator collator = Collator.getInstance();
            result.sort((a, b) -> {
                if (a.checked != b.checked) return a.checked ? -1 : 1;
                return collator.compare(a.label, b.label);
            });
            runOnUiThread(() -> showApps(result));
        }).start();
    }

    private void showApps(List<AppItem> result) {
        apps.clear();
        apps.addAll(result);
        appList.removeAllViews();
        for (AppItem item : apps) {
            View row = getLayoutInflater().inflate(R.layout.item_app, appList, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageDrawable(item.icon);
            ((TextView) row.findViewById(R.id.label)).setText(item.label);
            CheckBox check = row.findViewById(R.id.check);
            check.setChecked(item.checked);
            row.setOnClickListener(v -> {
                item.checked = !item.checked;
                check.setChecked(item.checked);
                Set<String> sel = prefs.getApps();
                if (item.checked) sel.add(item.pkg);
                else sel.remove(item.pkg);
                prefs.setApps(sel);
            });
            item.row = row;
            appList.addView(row);
        }
        applyFilter(searchBox.getText().toString());
    }

    /** 依名稱（或套件名稱）篩選，不分大小寫。 */
    private void applyFilter(String query) {
        if (apps.isEmpty()) return;
        String q = query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (AppItem item : apps) {
            boolean match = q.isEmpty()
                    || item.label.toLowerCase(Locale.ROOT).contains(q)
                    || item.pkg.toLowerCase(Locale.ROOT).contains(q);
            item.row.setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) shown++;
        }
        if (shown == 0) {
            emptyText.setText("找不到「" + query.trim() + "」");
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
