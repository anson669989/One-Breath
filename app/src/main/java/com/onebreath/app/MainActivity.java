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
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 設定頁：開權限、調倒數秒數、勾選要提醒的 App。 */
public class MainActivity extends Activity {

    private static class AppItem {
        String pkg;
        String label;
        Drawable icon;
        boolean checked;
    }

    private Prefs prefs;
    private final List<AppItem> apps = new ArrayList<>();
    private AppAdapter adapter;

    private TextView statusText, samsungHint, delayLabel, graceLabel;
    private Button accessBtn, appInfoBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        ListView list = new ListView(this);
        list.setDividerHeight(0);
        setContentView(list);

        View header = getLayoutInflater().inflate(R.layout.header_main, list, false);
        list.addHeaderView(header, null, false);

        statusText = header.findViewById(R.id.statusText);
        samsungHint = header.findViewById(R.id.samsungHint);
        accessBtn = header.findViewById(R.id.accessBtn);
        appInfoBtn = header.findViewById(R.id.appInfoBtn);
        delayLabel = header.findViewById(R.id.delayLabel);
        graceLabel = header.findViewById(R.id.graceLabel);

        accessBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        appInfoBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        Switch enableSwitch = header.findViewById(R.id.enableSwitch);
        enableSwitch.setChecked(prefs.isEnabled());
        enableSwitch.setOnCheckedChangeListener((b, on) -> prefs.setEnabled(on));

        SeekBar delaySeek = header.findViewById(R.id.delaySeek);
        delaySeek.setProgress(prefs.getDelaySeconds());
        updateDelayLabel(prefs.getDelaySeconds());
        delaySeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                prefs.setDelaySeconds(value);
                updateDelayLabel(value);
            }
        });

        SeekBar graceSeek = header.findViewById(R.id.graceSeek);
        graceSeek.setProgress(prefs.getGraceMinutes() - 1);
        updateGraceLabel(prefs.getGraceMinutes());
        graceSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                prefs.setGraceMinutes(value + 1);
                updateGraceLabel(value + 1);
            }
        });

        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object o = parent.getItemAtPosition(position);
            if (!(o instanceof AppItem)) return;
            AppItem item = (AppItem) o;
            item.checked = !item.checked;
            Set<String> selected = prefs.getApps();
            if (item.checked) selected.add(item.pkg);
            else selected.remove(item.pkg);
            prefs.setApps(selected);
            adapter.notifyDataSetChanged();
        });

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateDelayLabel(int s) {
        delayLabel.setText(s == 0 ? "倒數：不用等，直接可以選" : "倒數 " + s + " 秒後才能按「繼續」");
    }

    private void updateGraceLabel(int m) {
        graceLabel.setText("按「繼續」後 " + m + " 分鐘內不再提醒");
    }

    private void updateStatus() {
        boolean on = isServiceEnabled();
        if (on) {
            statusText.setText("✓ 無障礙權限已開啟，提醒運作中");
            statusText.setBackgroundColor(0xFFDCEFE6);
            statusText.setTextColor(0xFF22553F);
            accessBtn.setText("無障礙設定");
            samsungHint.setVisibility(View.GONE);
            appInfoBtn.setVisibility(View.GONE);
        } else {
            statusText.setText("✗ 還沒開啟無障礙權限，提醒不會出現");
            statusText.setBackgroundColor(0xFFF7E3DF);
            statusText.setTextColor(0xFF8A3324);
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
            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(result);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : getLayoutInflater().inflate(R.layout.item_app, parent, false);
            AppItem item = apps.get(position);
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(item.icon);
            ((TextView) v.findViewById(R.id.label)).setText(item.label);
            ((CheckBox) v.findViewById(R.id.check)).setChecked(item.checked);
            return v;
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
