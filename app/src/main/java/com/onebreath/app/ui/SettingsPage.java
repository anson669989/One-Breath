package com.onebreath.app.ui;

import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.onebreath.app.R;
import com.onebreath.app.data.AppLocale;
import com.onebreath.app.data.Prefs;

/**
 * 設定頁：全域時間（群組沒自訂時用這些）、介面語言、關於／隱私說明，
 * 最下面是「啟用提醒」總開關。改了立刻存。
 */
final class SettingsPage {

    private final MainActivity act;
    private final Prefs prefs;
    private final SeekRow delay, grace, remind, remindDelay;
    private final TextView langSystem, langZh, langEn;
    private final Switch masterSwitch;

    SettingsPage(MainActivity act, View root) {
        this.act = act;
        this.prefs = new Prefs(act);
        final Prefs p = prefs;

        delay = new SeekRow(root.findViewById(R.id.seekDelay), root.findViewById(R.id.labelDelay),
                0, 30, v -> SeekRow.seconds(act, v), p::setDelaySeconds);
        grace = new SeekRow(root.findViewById(R.id.seekGrace), root.findViewById(R.id.labelGrace),
                1, 30, v -> SeekRow.minutes(act, v), p::setGraceMinutes);
        remind = new SeekRow(root.findViewById(R.id.seekRemind), root.findViewById(R.id.labelRemind),
                1, 60, v -> SeekRow.everyMinutes(act, v), p::setRemindMinutes);
        remindDelay = new SeekRow(root.findViewById(R.id.seekRemindDelay),
                root.findViewById(R.id.labelRemindDelay),
                0, 30, v -> SeekRow.seconds(act, v), p::setRemindDelaySeconds);

        langSystem = root.findViewById(R.id.langSystem);
        langZh = root.findViewById(R.id.langZh);
        langEn = root.findViewById(R.id.langEn);
        langSystem.setOnClickListener(v -> chooseLanguage(AppLocale.SYSTEM));
        langZh.setOnClickListener(v -> chooseLanguage(AppLocale.ZH));
        langEn.setOnClickListener(v -> chooseLanguage(AppLocale.EN));
        styleLanguage(p.getLanguage());

        TextView version = root.findViewById(R.id.versionText);
        version.setText(act.getString(R.string.version, versionName()));

        masterSwitch = root.findViewById(R.id.masterSwitch);
        masterSwitch.setChecked(p.isEnabled());
        masterSwitch.setOnCheckedChangeListener((b, on) -> p.setEnabled(on));
        UiKit.bindToggleRow(root.findViewById(R.id.masterRow), masterSwitch);
    }

    void refresh() {
        delay.set(prefs.getDelaySeconds());
        grace.set(prefs.getGraceMinutes());
        remind.set(prefs.getRemindMinutes());
        remindDelay.set(prefs.getRemindDelaySeconds());
        styleLanguage(prefs.getLanguage());
        // 總開關（可能在別處被改過）
        if (masterSwitch.isChecked() != prefs.isEnabled()) masterSwitch.setChecked(prefs.isEnabled());
    }

    /** 存好語言後重建 Activity；MainActivity 會用 onSaveInstanceState 留在設定頁。 */
    private void chooseLanguage(String lang) {
        if (lang.equals(prefs.getLanguage())) return;
        prefs.setLanguage(lang);
        styleLanguage(lang);
        act.recreate();
    }

    private void styleLanguage(String lang) {
        UiKit.styleSegment(langSystem, AppLocale.SYSTEM.equals(lang));
        UiKit.styleSegment(langZh, AppLocale.ZH.equals(lang));
        UiKit.styleSegment(langEn, AppLocale.EN.equals(lang));
    }

    private String versionName() {
        try {
            String v = act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
            return v == null ? "?" : v;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
