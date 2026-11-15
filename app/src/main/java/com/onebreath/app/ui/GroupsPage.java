package com.onebreath.app.ui;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.onebreath.app.R;
import com.onebreath.app.data.Group;
import com.onebreath.app.data.GroupStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 群組頁：列出每個群組的卡片，可以開關、點進去編輯、新增群組。 */
final class GroupsPage {

    private static final int MAX_ICONS = 5;

    private final MainActivity act;
    private final LinearLayout list;
    private final View empty;
    private final LayoutInflater inflater;
    /** 圖示快取，避免每次回到這頁都重新向系統要。 */
    private final Map<String, Drawable> iconCache = new HashMap<>();

    GroupsPage(MainActivity act, View root) {
        this.act = act;
        this.inflater = LayoutInflater.from(act);
        list = root.findViewById(R.id.groupList);
        empty = root.findViewById(R.id.groupsEmpty);
        root.findViewById(R.id.addGroupBtn).setOnClickListener(v -> showCreateDialog());
        root.findViewById(R.id.emptyAddBtn).setOnClickListener(v -> showCreateDialog());
    }

    void refresh() {
        List<Group> groups = GroupStore.get(act).getGroups();
        list.removeAllViews();
        empty.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
        for (Group g : groups) {
            list.addView(buildCard(g));
        }
    }

    void showCreateDialog() {
        // 範本只用來預填名稱，跟著目前的介面語言；存下去之後就是使用者自己的群組名稱，不會再翻譯
        final String[] presets = act.getResources().getStringArray(R.array.group_presets);
        String[] items = new String[presets.length + 1];
        System.arraycopy(presets, 0, items, 0, presets.length);
        items[presets.length] = act.getString(R.string.groups_custom_name);
        new AlertDialog.Builder(act)
                .setTitle(R.string.new_group_title)
                .setItems(items, (d, which) -> {
                    String name = which < presets.length ? presets[which] : "";
                    act.startActivity(GroupEditActivity.newGroupIntent(act, name));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private View buildCard(Group g) {
        View card = inflater.inflate(R.layout.item_group, list, false);
        TextView name = card.findViewById(R.id.groupName);
        TextView meta = card.findViewById(R.id.groupMeta);
        Switch sw = card.findViewById(R.id.groupSwitch);
        LinearLayout icons = card.findViewById(R.id.groupIcons);
        LinearLayout chips = card.findViewById(R.id.groupChips);

        String title = g.name == null || g.name.trim().isEmpty() ? act.getString(R.string.unnamed) : g.name;
        name.setText(title);

        Resources res = act.getResources();
        String apps = res.getQuantityString(R.plurals.group_meta_apps, g.apps.size(), g.apps.size());
        if (g.sites.isEmpty()) {
            meta.setText(apps);
        } else {
            meta.setText(act.getString(R.string.two_parts, apps,
                    res.getQuantityString(R.plurals.group_meta_sites, g.sites.size(), g.sites.size())));
        }

        fillIcons(icons, g);
        fillChips(chips, g);

        sw.setChecked(g.enabled);
        sw.setContentDescription(act.getString(R.string.group_switch_cd, title));
        applyEnabledLook(card, g.enabled);
        final String groupId = g.id;
        sw.setOnCheckedChangeListener((b, on) -> {
            // 重新讀一次，免得蓋掉別處的修改；只改 enabled 立刻存
            GroupStore store = GroupStore.get(act);
            Group fresh = store.find(groupId);
            if (fresh == null) return;
            fresh.enabled = on;
            store.save(fresh);
            applyEnabledLook(card, on);
        });

        View.OnClickListener open = v -> act.startActivity(GroupEditActivity.editIntent(act, groupId));
        card.setOnClickListener(open);
        chips.setOnClickListener(open);
        return card;
    }

    private void applyEnabledLook(View card, boolean enabled) {
        float a = enabled ? 1f : 0.45f;
        card.findViewById(R.id.groupIcons).setAlpha(a);
        card.findViewById(R.id.groupChips).setAlpha(a);
        card.findViewById(R.id.groupName).setAlpha(enabled ? 1f : 0.7f);
    }

    private void fillIcons(LinearLayout row, Group g) {
        row.removeAllViews();
        if (g.apps.isEmpty()) {
            TextView t = new TextView(act);
            t.setText(R.string.group_no_apps);
            t.setTextColor(act.getColor(R.color.text_muted));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.addView(t);
            return;
        }
        int size = UiKit.dp(act, 30);
        int gap = UiKit.dp(act, 8);
        int shown = 0;
        int missing = 0;
        for (String pkg : g.apps) {
            if (shown >= MAX_ICONS) break;
            Drawable d = loadIcon(pkg);
            if (d == null) {
                missing++;
                continue;
            }
            ImageView iv = new ImageView(act);
            iv.setImageDrawable(d);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = gap;
            row.addView(iv, lp);
            shown++;
        }
        int more = g.apps.size() - shown - missing;
        if (more > 0) {
            TextView t = new TextView(act);
            t.setText("+" + more);
            t.setTextColor(act.getColor(R.color.text_secondary));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.addView(t);
        }
    }

    private Drawable loadIcon(String pkg) {
        if (iconCache.containsKey(pkg)) return iconCache.get(pkg);
        Drawable d;
        try {
            d = act.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            d = null; // 已解除安裝
        }
        iconCache.put(pkg, d);
        return d;
    }

    private void fillChips(LinearLayout row, Group g) {
        row.removeAllViews();
        boolean any = false;
        if (g.pauseOnOpen) {
            row.addView(UiKit.chip(act, act.getString(R.string.chip_pause), false));
            any = true;
        }
        if (g.remindInUse) {
            row.addView(UiKit.chip(act, act.getString(R.string.feature_remind), false));
            any = true;
        }
        if (g.blockWeb || !g.sites.isEmpty()) {
            row.addView(UiKit.chip(act, act.getString(R.string.chip_web), false));
            any = true;
        }
        if (g.customTiming) {
            row.addView(UiKit.chip(act, act.getString(R.string.chip_custom), true));
        }
        if (!any) {
            row.addView(UiKit.chip(act, act.getString(R.string.chip_none), true));
        }
    }
}
