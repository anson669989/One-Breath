package com.onebreath.app.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.onebreath.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 群組編輯頁的 App 清單（ListView + ViewHolder），搜尋篩選也在這裡做。 */
final class AppListAdapter extends BaseAdapter {

    static final class AppEntry {
        final String pkg;
        final String label;
        final Drawable icon;
        /** 對應網站（例如 "x.com · twitter.com"），沒有就是 null。 */
        final String domains;
        /** 這個 App 目前屬於別的群組時，那個群組的名稱；否則 null。 */
        final String otherGroup;
        final String search;

        AppEntry(String pkg, String label, Drawable icon, String domains, String otherGroup) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
            this.domains = domains;
            this.otherGroup = otherGroup;
            this.search = (label + " " + pkg + " " + (domains == null ? "" : domains))
                    .toLowerCase(Locale.ROOT);
        }
    }

    private static final class Holder {
        ImageView icon;
        TextView label, sub, tag;
        CheckBox check;
    }

    /** 建立時傳進來的 Activity（已套用 App 語言），用來取字串。 */
    private final Context context;
    private final LayoutInflater inflater;
    /** 正在編輯的群組的 apps 集合（直接修改它）。 */
    private final Set<String> selected;
    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private String query = "";

    AppListAdapter(Context c, Set<String> selected) {
        this.context = c;
        this.inflater = LayoutInflater.from(c);
        this.selected = selected;
    }

    void setItems(List<AppEntry> items) {
        all.clear();
        all.addAll(items);
        applyFilter();
    }

    void setQuery(String q) {
        query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }


    /** 切換第 position 列的勾選，回傳新的狀態。 */
    boolean toggle(int position) {
        AppEntry e = shown.get(position);
        boolean now;
        if (selected.contains(e.pkg)) {
            selected.remove(e.pkg);
            now = false;
        } else {
            selected.add(e.pkg);
            now = true;
        }
        notifyDataSetChanged();
        return now;
    }

    /** 已勾選、且手機上還裝著的 App 數。 */
    int checkedCount() {
        int n = 0;
        for (AppEntry e : all) {
            if (selected.contains(e.pkg)) n++;
        }
        return n;
    }

    private void applyFilter() {
        shown.clear();
        for (AppEntry e : all) {
            if (query.isEmpty() || e.search.contains(query)) shown.add(e);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return shown.size();
    }

    @Override
    public AppEntry getItem(int position) {
        return shown.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app, parent, false);
            h = new Holder();
            h.icon = convertView.findViewById(R.id.appIcon);
            h.label = convertView.findViewById(R.id.appLabel);
            h.sub = convertView.findViewById(R.id.appSub);
            h.tag = convertView.findViewById(R.id.appTag);
            h.check = convertView.findViewById(R.id.appCheck);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        AppEntry e = shown.get(position);
        boolean checked = selected.contains(e.pkg);
        h.icon.setImageDrawable(e.icon);
        h.label.setText(e.label);
        h.check.setChecked(checked);

        if (e.domains != null) {
            h.sub.setText(context.getString(R.string.app_sites, e.domains));
            h.sub.setVisibility(View.VISIBLE);
        } else {
            h.sub.setVisibility(View.GONE);
        }

        if (e.otherGroup != null) {
            h.tag.setText(context.getString(checked ? R.string.app_moves_here : R.string.app_in_group,
                    e.otherGroup));
            h.tag.setVisibility(View.VISIBLE);
        } else {
            h.tag.setVisibility(View.GONE);
        }

        convertView.setContentDescription(context.getString(
                checked ? R.string.app_cd_checked : R.string.app_cd_unchecked, e.label));
        return convertView;
    }
}
