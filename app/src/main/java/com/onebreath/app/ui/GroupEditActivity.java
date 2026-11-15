package com.onebreath.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.onebreath.app.R;
import com.onebreath.app.data.AppLocale;
import com.onebreath.app.data.Group;
import com.onebreath.app.data.GroupStore;
import com.onebreath.app.data.SiteCatalog;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 編輯一個群組。上方分成「設定」與「App」兩段：
 * 設定段是一般的 ScrollView；App 段用 ListView 自己捲動（App 可能有兩三百個）。
 * 只有按「儲存」才寫入；有改動時按返回會先問要不要儲存。
 */
public class GroupEditActivity extends Activity {

    static final String EXTRA_GROUP_ID = "com.onebreath.app.extra.GROUP_ID";
    static final String EXTRA_NEW_NAME = "com.onebreath.app.extra.NEW_NAME";

    private static final int TAB_SETTINGS = 0;
    private static final int TAB_APPS = 1;

    private static final String S_IS_NEW = "isNew";
    private static final String S_ID = "id";
    private static final String S_NAME = "name";
    private static final String S_PAUSE = "pause";
    private static final String S_REMIND = "remind";
    private static final String S_WEB = "web";
    private static final String S_CUSTOM = "custom";
    private static final String S_DELAY = "delay";
    private static final String S_REMIND_MIN = "remindMin";
    private static final String S_REMIND_DELAY = "remindDelay";
    private static final String S_APPS = "apps";
    private static final String S_SITES = "sites";
    private static final String S_INITIAL = "initial";
    private static final String S_TAB = "tab";

    /** 新群組（還沒存），name 只是預填。 */
    static Intent newGroupIntent(Context c, String name) {
        return new Intent(c, GroupEditActivity.class)
                .putExtra(EXTRA_NEW_NAME, name == null ? "" : name);
    }

    /** 編輯既有群組。 */
    static Intent editIntent(Context c, String groupId) {
        return new Intent(c, GroupEditActivity.class).putExtra(EXTRA_GROUP_ID, groupId);
    }

    private GroupStore store;
    private Group group;
    private boolean isNew;
    private String initialSig;
    private int tab = TAB_SETTINGS;
    private boolean appsLoaded;

    private TextView editTitle, tabSettings, tabApps;
    private View paneSettings, paneApps;
    private EditText editName, siteInput, searchBox;
    private Switch switchPause, switchRemind, switchWeb, switchCustom;
    private View timingOff, timingBox;
    private SeekRow seekDelay, seekRemind, seekRemindDelay;
    private LinearLayout siteList;
    private TextView siteEmpty, siteError, appCount, appEmpty;
    private Button btnDelete;
    private ListView appList;
    private AppListAdapter adapter;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_edit);
        store = GroupStore.get(this);

        bindViews();

        if (savedInstanceState != null) {
            if (!restore(savedInstanceState)) {
                finish();
                return;
            }
        } else {
            String id = getIntent().getStringExtra(EXTRA_GROUP_ID);
            if (id != null) {
                group = store.find(id);
                if (group == null) {
                    Toast.makeText(this, R.string.edit_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                isNew = false;
            } else {
                String name = getIntent().getStringExtra(EXTRA_NEW_NAME);
                group = store.newGroup(name == null ? "" : name);
                isNew = true;
            }
        }

        populateUi();

        if (savedInstanceState != null) {
            initialSig = savedInstanceState.getString(S_INITIAL);
            tab = savedInstanceState.getInt(S_TAB, TAB_SETTINGS);
        }
        if (initialSig == null) initialSig = signature();
        if (savedInstanceState == null) {
            // 用範本建立的新群組，下一步通常是挑 App；自訂名稱則先填名字
            tab = isNew && !group.name.trim().isEmpty() ? TAB_APPS : TAB_SETTINGS;
        }

        bindListeners();

        adapter = new AppListAdapter(this, group.apps);
        appList.setAdapter(adapter);
        appList.setEmptyView(appEmpty);
        appList.setOnItemClickListener((parent, view, position, id) -> {
            adapter.toggle(position);
            updateCount();
        });
        updateCount();
        loadApps();

        showTab(tab);
    }

    // ---------------------------------------------------------------- 綁定

    private void bindViews() {
        editTitle = findViewById(R.id.editTitle);
        tabSettings = findViewById(R.id.tabSettings);
        tabApps = findViewById(R.id.tabApps);
        paneSettings = findViewById(R.id.paneSettings);
        paneApps = findViewById(R.id.paneApps);
        editName = findViewById(R.id.editName);
        switchPause = findViewById(R.id.switchPause);
        switchRemind = findViewById(R.id.switchRemind);
        switchWeb = findViewById(R.id.switchWeb);
        switchCustom = findViewById(R.id.switchCustom);
        timingOff = findViewById(R.id.timingOff);
        timingBox = findViewById(R.id.timingBox);
        siteList = findViewById(R.id.siteList);
        siteEmpty = findViewById(R.id.siteEmpty);
        siteError = findViewById(R.id.siteError);
        siteInput = findViewById(R.id.siteInput);
        btnDelete = findViewById(R.id.btnDelete);
        searchBox = findViewById(R.id.searchBox);
        appCount = findViewById(R.id.appCount);
        appEmpty = findViewById(R.id.appEmpty);
        appList = findViewById(R.id.appList);

        seekDelay = new SeekRow(findViewById(R.id.seekGDelay), findViewById(R.id.labelGDelay),
                0, 30, v -> SeekRow.seconds(this, v), null);
        seekRemind = new SeekRow(findViewById(R.id.seekGRemind), findViewById(R.id.labelGRemind),
                1, 60, v -> SeekRow.everyMinutes(this, v), null);
        seekRemindDelay = new SeekRow(findViewById(R.id.seekGRemindDelay),
                findViewById(R.id.labelGRemindDelay), 0, 30, v -> SeekRow.seconds(this, v), null);
    }

    private void bindListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> attemptExit());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());
        tabSettings.setOnClickListener(v -> showTab(TAB_SETTINGS));
        tabApps.setOnClickListener(v -> showTab(TAB_APPS));

        UiKit.bindToggleRow(findViewById(R.id.rowPause), switchPause);
        UiKit.bindToggleRow(findViewById(R.id.rowRemind), switchRemind);
        UiKit.bindToggleRow(findViewById(R.id.rowWeb), switchWeb);
        UiKit.bindToggleRow(findViewById(R.id.rowCustom), switchCustom);
        switchCustom.setOnCheckedChangeListener((b, on) -> updateTimingVisibility());

        editName.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                editName.setError(null);
                updateTitle();
            }
        });

        findViewById(R.id.siteAdd).setOnClickListener(v -> addSite());
        siteInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSite();
                return true;
            }
            return false;
        });
        siteInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                siteError.setVisibility(View.GONE);
            }
        });

        searchBox.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                adapter.setQuery(s.toString());
                updateEmptyText();
            }
        });
    }

    private void populateUi() {
        editName.setText(group.name == null ? "" : group.name);
        switchPause.setChecked(group.pauseOnOpen);
        switchRemind.setChecked(group.remindInUse);
        switchWeb.setChecked(group.blockWeb);
        switchCustom.setChecked(group.customTiming);
        seekDelay.set(group.delaySeconds);
        seekRemind.set(group.remindMinutes);
        seekRemindDelay.set(group.remindDelaySeconds);
        updateTimingVisibility();
        renderSites();
        btnDelete.setVisibility(isNew ? View.GONE : View.VISIBLE);
        updateTitle();
    }

    // ---------------------------------------------------------------- 畫面狀態

    private void showTab(int t) {
        tab = t;
        boolean apps = t == TAB_APPS;
        paneSettings.setVisibility(apps ? View.GONE : View.VISIBLE);
        paneApps.setVisibility(apps ? View.VISIBLE : View.GONE);
        styleTab(tabSettings, !apps);
        styleTab(tabApps, apps);
        hideKeyboard();
    }

    private void styleTab(TextView t, boolean selected) {
        UiKit.styleSegment(t, selected);
    }

    private void updateTitle() {
        String n = editName.getText().toString().trim();
        if (!n.isEmpty()) {
            editTitle.setText(n);
        } else {
            editTitle.setText(isNew ? R.string.new_group_title : R.string.unnamed);
        }
    }

    private void updateTimingVisibility() {
        boolean custom = switchCustom.isChecked();
        timingBox.setVisibility(custom ? View.VISIBLE : View.GONE);
        timingOff.setVisibility(custom ? View.GONE : View.VISIBLE);
    }

    private void updateCount() {
        int n = appsLoaded ? adapter.checkedCount() : group.apps.size();
        tabApps.setText(n > 0 ? getString(R.string.edit_tab_apps_count, n) : getString(R.string.edit_tab_apps));
        appCount.setText(n == 0 ? getString(R.string.edit_count_none)
                : getResources().getQuantityString(R.plurals.edit_count, n, n));
    }

    private void updateEmptyText() {
        String q = searchBox.getText().toString().trim();
        if (!appsLoaded) {
            appEmpty.setText(R.string.edit_loading);
        } else if (q.isEmpty()) {
            appEmpty.setText(R.string.edit_no_apps_found);
        } else {
            appEmpty.setText(getString(R.string.edit_no_match, q));
        }
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    // ---------------------------------------------------------------- 自訂網站

    private void addSite() {
        String raw = siteInput.getText().toString().trim();
        if (raw.isEmpty()) return;
        String domain = SiteCatalog.normalize(raw);
        if (domain == null) {
            showSiteError(getString(R.string.edit_site_invalid));
            return;
        }
        if (group.sites.contains(domain)) {
            showSiteError(getString(R.string.edit_site_dup, domain));
            return;
        }
        group.sites.add(domain);
        siteInput.setText("");
        siteError.setVisibility(View.GONE);
        renderSites();
    }

    private void showSiteError(String msg) {
        siteError.setText(msg);
        siteError.setVisibility(View.VISIBLE);
    }

    private void renderSites() {
        siteList.removeAllViews();
        for (String domain : group.sites) {
            View row = getLayoutInflater().inflate(R.layout.item_site, siteList, false);
            ((TextView) row.findViewById(R.id.siteName)).setText(domain);
            Button remove = row.findViewById(R.id.siteRemove);
            remove.setContentDescription(getString(R.string.edit_site_remove_cd, domain));
            remove.setOnClickListener(v -> {
                group.sites.remove(domain);
                renderSites();
            });
            siteList.addView(row);
        }
        siteEmpty.setVisibility(group.sites.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- App 清單

    private void loadApps() {
        final Set<String> initiallySelected = new HashSet<>(group.apps);
        final String myId = group.id;
        final String self = getPackageName();
        final PackageManager pm = getPackageManager();
        // 背景執行緒用不到 Activity 的 View，但字串要先在這裡用 Activity（已套用 App 語言）取好
        final String unnamedGroup = getString(R.string.unnamed_group);
        final String otherGroup = getString(R.string.other_group);
        new Thread(() -> {
            Map<String, String> names = new HashMap<>();
            for (Group g : store.getGroups()) {
                String n = g.name == null || g.name.trim().isEmpty() ? unnamedGroup : g.name;
                names.put(g.id, n);
            }
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
            Set<String> seen = new HashSet<>();
            List<AppListAdapter.AppEntry> result = new ArrayList<>();
            for (ResolveInfo ri : infos) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(self) || !seen.add(pkg)) continue;
                Set<String> domains = SiteCatalog.domainsFor(pkg);
                String dom = domains == null || domains.isEmpty() ? null : String.join(" · ", domains);
                String gid = store.groupIdOfApp(pkg);
                String other = null;
                if (gid != null && !gid.equals(myId)) {
                    other = names.containsKey(gid) ? names.get(gid) : otherGroup;
                }
                result.add(new AppListAdapter.AppEntry(pkg, ri.loadLabel(pm).toString(),
                        ri.loadIcon(pm), dom, other));
            }
            Collator collator = Collator.getInstance();
            result.sort((a, b) -> {
                boolean ca = initiallySelected.contains(a.pkg);
                boolean cb = initiallySelected.contains(b.pkg);
                if (ca != cb) return ca ? -1 : 1;
                return collator.compare(a.label, b.label);
            });
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                appsLoaded = true;
                adapter.setItems(result);
                updateEmptyText();
                updateCount();
            });
        }).start();
    }

    // ---------------------------------------------------------------- 儲存／刪除／離開

    /** 把畫面上的值寫回 group（apps、sites 本來就直接改在 group 上）。 */
    private void collect() {
        group.name = editName.getText().toString().trim();
        group.pauseOnOpen = switchPause.isChecked();
        group.remindInUse = switchRemind.isChecked();
        group.blockWeb = switchWeb.isChecked();
        group.customTiming = switchCustom.isChecked();
        group.delaySeconds = seekDelay.get();
        group.remindMinutes = seekRemind.get();
        group.remindDelaySeconds = seekRemindDelay.get();
    }

    private String signature() {
        collect();
        return group.name + '|' + group.pauseOnOpen + '|' + group.remindInUse + '|' + group.blockWeb
                + '|' + group.customTiming + '|' + group.delaySeconds + '|' + group.remindMinutes
                + '|' + group.remindDelaySeconds + '|' + new TreeSet<>(group.apps)
                + '|' + new TreeSet<>(group.sites);
    }

    private boolean isDirty() {
        return !signature().equals(initialSig);
    }

    private void save() {
        collect();
        if (group.name.isEmpty()) {
            showTab(TAB_SETTINGS);
            editName.setError(getString(R.string.edit_name_required));
            editName.requestFocus();
            return;
        }
        store.save(group);
        Toast.makeText(this, getString(R.string.edit_saved, group.name), Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        String n = editName.getText().toString().trim();
        new AlertDialog.Builder(this)
                .setTitle(n.isEmpty() ? getString(R.string.edit_delete_title_unnamed)
                        : getString(R.string.edit_delete_title, n))
                .setMessage(R.string.edit_delete_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.delete(group.id);
                    Toast.makeText(this, R.string.edit_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void attemptExit() {
        if (!isDirty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_unsaved_title)
                .setMessage(R.string.edit_unsaved_msg)
                .setPositiveButton(R.string.save, (d, w) -> save())
                .setNegativeButton(R.string.edit_discard, (d, w) -> finish())
                .setNeutralButton(R.string.edit_keep_editing, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        attemptExit();
    }

    // ---------------------------------------------------------------- 旋轉時保留編輯中的內容

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (group == null) return;
        collect();
        out.putBoolean(S_IS_NEW, isNew);
        out.putString(S_ID, group.id);
        out.putString(S_NAME, group.name);
        out.putBoolean(S_PAUSE, group.pauseOnOpen);
        out.putBoolean(S_REMIND, group.remindInUse);
        out.putBoolean(S_WEB, group.blockWeb);
        out.putBoolean(S_CUSTOM, group.customTiming);
        out.putInt(S_DELAY, group.delaySeconds);
        out.putInt(S_REMIND_MIN, group.remindMinutes);
        out.putInt(S_REMIND_DELAY, group.remindDelaySeconds);
        out.putStringArrayList(S_APPS, new ArrayList<>(group.apps));
        out.putStringArrayList(S_SITES, new ArrayList<>(group.sites));
        out.putString(S_INITIAL, initialSig);
        out.putInt(S_TAB, tab);
    }

    /** 從 Bundle 還原編輯中的群組；既有群組已被刪掉時回傳 false。 */
    private boolean restore(Bundle s) {
        isNew = s.getBoolean(S_IS_NEW, false);
        String id = s.getString(S_ID);
        String name = s.getString(S_NAME, "");
        if (isNew || id == null) {
            isNew = true;
            group = store.newGroup(name);
            if (id != null) group.id = id;
        } else {
            group = store.find(id);
            if (group == null) return false;
        }
        group.name = name;
        group.pauseOnOpen = s.getBoolean(S_PAUSE, group.pauseOnOpen);
        group.remindInUse = s.getBoolean(S_REMIND, group.remindInUse);
        group.blockWeb = s.getBoolean(S_WEB, group.blockWeb);
        group.customTiming = s.getBoolean(S_CUSTOM, group.customTiming);
        group.delaySeconds = s.getInt(S_DELAY, group.delaySeconds);
        group.remindMinutes = s.getInt(S_REMIND_MIN, group.remindMinutes);
        group.remindDelaySeconds = s.getInt(S_REMIND_DELAY, group.remindDelaySeconds);
        ArrayList<String> apps = s.getStringArrayList(S_APPS);
        if (apps != null) {
            group.apps.clear();
            group.apps.addAll(apps);
        }
        ArrayList<String> sites = s.getStringArrayList(S_SITES);
        if (sites != null) {
            group.sites.clear();
            group.sites.addAll(sites);
        }
        return true;
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int count, int after) {
        }
    }
}
