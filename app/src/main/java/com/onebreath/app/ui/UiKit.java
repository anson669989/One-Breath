package com.onebreath.app.ui;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.onebreath.app.R;
import com.onebreath.app.service.PauseService;

/** 幾個畫面共用的小工具。 */
final class UiKit {

    private UiKit() {
    }

    static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics()));
    }

    /** 無障礙服務（PauseService）是否已在系統設定裡開啟。 */
    static boolean isServiceEnabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(c, PauseService.class);
        for (String s : enabled.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    /** 小圓角標籤（群組卡片上顯示開了哪些功能）。 */
    static TextView chip(Context c, String text, boolean muted) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setSingleLine(true);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(c.getColor(muted ? R.color.text_muted : R.color.chip_text));
        t.setBackgroundResource(R.drawable.bg_chip);
        int h = dp(c, 10), v = dp(c, 4);
        t.setPadding(h, v, h, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 6);
        t.setLayoutParams(lp);
        return t;
    }

    /** 讓整列（標題＋說明＋Switch）都可以點來切換。 */
    static void bindToggleRow(View row, Switch sw) {
        row.setOnClickListener(v -> sw.toggle());
    }
}
