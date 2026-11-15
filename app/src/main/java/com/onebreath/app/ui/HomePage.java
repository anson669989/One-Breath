package com.onebreath.app.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.onebreath.app.R;
import com.onebreath.app.data.GroupStore;
import com.onebreath.app.data.Prefs;

/** 首頁：權限狀態、今天的次數、群組摘要。（「啟用提醒」總開關在設定頁） */
final class HomePage {

    private final MainActivity act;
    private final Prefs prefs;

    private final View statusCard;
    private final TextView statusTitle, statusBody, restrictHint;
    private final Button accessBtn, appInfoBtn;
    private final TextView todayTotal, todayResisted, todayTotalLabel, todayResistedLabel;
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
        todayTotal = root.findViewById(R.id.todayTotal);
        todayResisted = root.findViewById(R.id.todayResisted);
        todayTotalLabel = root.findViewById(R.id.todayTotalLabel);
        todayResistedLabel = root.findViewById(R.id.todayResistedLabel);
        summaryCard = root.findViewById(R.id.summaryCard);
        summaryText = root.findViewById(R.id.summaryText);
        summaryHint = root.findViewById(R.id.summaryHint);
        emptyCard = root.findViewById(R.id.emptyCard);

        accessBtn.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        appInfoBtn.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + act.getPackageName()))));

        summaryCard.setOnClickListener(v -> act.selectTab(MainActivity.TAB_GROUPS));
        root.findViewById(R.id.emptyCreateBtn).setOnClickListener(v -> act.startCreateGroup());
    }

    void refresh() {
        // 權限
        boolean on = UiKit.isServiceEnabled(act);
        if (on) {
            statusCard.setBackgroundResource(R.drawable.bg_status_ok);
            statusTitle.setText(R.string.status_ok_title);
            statusTitle.setTextColor(act.getColor(R.color.ok_text));
            statusBody.setText(R.string.status_ok_body);
            accessBtn.setVisibility(View.GONE);
            restrictHint.setVisibility(View.GONE);
            appInfoBtn.setVisibility(View.GONE);
        } else {
            statusCard.setBackgroundResource(R.drawable.bg_status_off);
            statusTitle.setText(R.string.status_off_title);
            statusTitle.setTextColor(act.getColor(R.color.warn_text));
            statusBody.setText(R.string.status_off_body);
            accessBtn.setVisibility(View.VISIBLE);
            restrictHint.setVisibility(View.VISIBLE);
            appInfoBtn.setVisibility(View.VISIBLE);
        }

        // 總開關狀態（在設定頁切換，回到首頁時重讀）
        updateMasterHint();

        // 今天
        int total = prefs.getTodayTotal();
        int resisted = prefs.getTodayResisted();
        todayTotal.setText(String.valueOf(total));
        todayResisted.setText(String.valueOf(resisted));
        Resources res = act.getResources();
        todayTotalLabel.setText(res.getQuantityString(R.plurals.home_breaths, total));
        todayResistedLabel.setText(res.getQuantityString(R.plurals.home_resisted, resisted));

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
            summaryText.setText(act.getString(R.string.two_parts,
                    res.getQuantityString(R.plurals.home_summary_groups, groups, groups),
                    res.getQuantityString(R.plurals.home_summary_apps, apps, apps)));
        }
    }

    private void updateMasterHint() {
        summaryHint.setText(prefs.isEnabled()
                ? R.string.home_summary_hint
                : R.string.home_summary_hint_paused);
    }

    private void safeStart(Intent i) {
        try {
            act.startActivity(i);
        } catch (Exception e) {
            act.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
