package com.onebreath.app.ui;

import android.content.pm.PackageManager;
import android.view.View;
import android.widget.TextView;

import com.onebreath.app.R;
import com.onebreath.app.data.Prefs;

/** 設定頁：全域時間（群組沒自訂時用這些），以及關於／隱私說明。改了立刻存。 */
final class SettingsPage {

    private final MainActivity act;
    private final Prefs prefs;
    private final SeekRow delay, grace, remind, remindDelay;

    SettingsPage(MainActivity act, View root) {
        this.act = act;
        this.prefs = new Prefs(act);
        final Prefs p = prefs;

        delay = new SeekRow(root.findViewById(R.id.seekDelay), root.findViewById(R.id.labelDelay),
                0, 30, SeekRow::seconds, p::setDelaySeconds);
        grace = new SeekRow(root.findViewById(R.id.seekGrace), root.findViewById(R.id.labelGrace),
                1, 30, SeekRow::minutes, p::setGraceMinutes);
        remind = new SeekRow(root.findViewById(R.id.seekRemind), root.findViewById(R.id.labelRemind),
                1, 60, SeekRow::everyMinutes, p::setRemindMinutes);
        remindDelay = new SeekRow(root.findViewById(R.id.seekRemindDelay),
                root.findViewById(R.id.labelRemindDelay),
                0, 30, SeekRow::seconds, p::setRemindDelaySeconds);

        TextView version = root.findViewById(R.id.versionText);
        version.setText("喘口氣 版本 " + versionName());
    }

    void refresh() {
        delay.set(prefs.getDelaySeconds());
        grace.set(prefs.getGraceMinutes());
        remind.set(prefs.getRemindMinutes());
        remindDelay.set(prefs.getRemindDelaySeconds());
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
