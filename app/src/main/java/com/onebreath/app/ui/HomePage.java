package com.onebreath.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import com.onebreath.app.R;
import com.onebreath.app.data.GroupStore;
import com.onebreath.app.data.Prefs;

/** 首頁：權限狀態、總開關、今天的次數、群組摘要。 */
final class HomePage {

    private final MainActivity act;
    private final Prefs prefs;

    private final View statusCard;
    private final TextView statusTitle, statusBody, restrictHint;
    private final Button accessBtn, appInfoBtn;
    private final Switch masterSwitch;
    private final TextView todayTotal, todayResisted;
    private final View summaryCard, emptyCard;
    private final TextView summaryText, summaryHint;

    HomePage(MainActivity act, View root) {
        this.act = act;
        this.prefs = new Prefs(act);

        statusCard = root.findViewById(R.id.statusCard);
        statusTitle = root.findViewById(R.id.statusTitle);
        statusBody = root.findViewById(R.id.statusBody);
        restrictHint = root.findViewById(R.id.restrictHint);
        accessBtn = root.findViewById(R.id.accessBtn);
        appInfoBtn = root.findViewById(R.id.appInfoBtn);
        masterSwitch = root.findViewById(R.id.masterSwitch);
        todayTotal = root.findViewById(R.id.todayTotal);
        todayResisted = root.findViewById(R.id.todayResisted);
        summaryCard = root.findViewById(R.id.summaryCard);
        summaryText = root.findViewById(R.id.summaryText);
        summaryHint = root.findViewById(R.id.summaryHint);
        emptyCard = root.findViewById(R.id.emptyCard);

        accessBtn.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        appInfoBtn.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + act.getPackageName()))));

        masterSwitch.setChecked(prefs.isEnabled());
        masterSwitch.setOnCheckedChangeListener((b, on) -> {
            prefs.setEnabled(on);
            updateMasterHint();
        });
        UiKit.bindToggleRow(root.findViewById(R.id.masterRow), masterSwitch);

        summaryCard.setOnClickListener(v -> act.selectTab(MainActivity.TAB_GROUPS));
        root.findViewById(R.id.emptyCreateBtn).setOnClickListener(v -> act.startCreateGroup());
    }

    void refresh() {
        // 權限
        boolean on = UiKit.isServiceEnabled(act);
        if (on) {
            statusCard.setBackgroundResource(R.drawable.bg_status_ok);
            statusTitle.setText("✓ 運作中");
            statusTitle.setTextColor(act.getColor(R.color.ok_text));
            statusBody.setText("無障礙權限已開啟，打開群組裡的 App 前會先喘口氣。");
            accessBtn.setVisibility(View.GONE);
            restrictHint.setVisibility(View.GONE);
            appInfoBtn.setVisibility(View.GONE);
        } else {
            statusCard.setBackgroundResource(R.drawable.bg_status_off);
            statusTitle.setText("✗ 還沒開啟無障礙權限");
            statusTitle.setTextColor(act.getColor(R.color.warn_text));
            statusBody.setText("沒有這個權限，喘口氣就不知道你打開了哪個 App，提醒不會出現。"
                    + "按下面按鈕 → 已安裝的應用程式 → 喘口氣 → 開啟。");
            accessBtn.setVisibility(View.VISIBLE);
            restrictHint.setVisibility(View.VISIBLE);
            appInfoBtn.setVisibility(View.VISIBLE);
        }

        // 總開關（可能在別處被改過）
        if (masterSwitch.isChecked() != prefs.isEnabled()) masterSwitch.setChecked(prefs.isEnabled());
        updateMasterHint();

        // 今天
        todayTotal.setText(String.valueOf(prefs.getTodayTotal()));
        todayResisted.setText(String.valueOf(prefs.getTodayResisted()));

        // 群組摘要
        GroupStore store = GroupStore.get(act);
        int groups = store.getGroups().size();
        if (groups == 0) {
            summaryCard.setVisibility(View.GONE);
            emptyCard.setVisibility(View.VISIBLE);
        } else {
            summaryCard.setVisibility(View.VISIBLE);
            emptyCard.setVisibility(View.GONE);
            int apps = store.watchedAppCount();
            summaryText.setText(groups + " 個群組 · " + apps + " 個 App 提醒中");
        }
    }

    private void updateMasterHint() {
        summaryHint.setText(prefs.isEnabled()
                ? "點這裡管理群組與 App"
                : "提醒已全部暫停 · 點這裡管理群組與 App");
    }

    private void safeStart(Intent i) {
        try {
            act.startActivity(i);
        } catch (Exception e) {
            act.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
