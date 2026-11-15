package com.onebreath.app.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.onebreath.app.R;

/** 主畫面：下方導覽列切換「首頁／群組／設定」三頁，每頁由自己的小控制器負責。 */
public class MainActivity extends Activity {

    static final int TAB_HOME = 0;
    static final int TAB_GROUPS = 1;
    static final int TAB_SETTINGS = 2;
    private static final String STATE_TAB = "tab";

    private View[] pages;
    private View[] navItems;
    private ImageView[] navIcons;
    private TextView[] navLabels;

    private HomePage homePage;
    private GroupsPage groupsPage;
    private SettingsPage settingsPage;

    private int currentTab = TAB_HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pages = new View[]{
                findViewById(R.id.pageHome),
                findViewById(R.id.pageGroups),
                findViewById(R.id.pageSettings)};
        navItems = new View[]{
                findViewById(R.id.navHome),
                findViewById(R.id.navGroups),
                findViewById(R.id.navSettings)};
        navIcons = new ImageView[]{
                findViewById(R.id.navHomeIcon),
                findViewById(R.id.navGroupsIcon),
                findViewById(R.id.navSettingsIcon)};
        navLabels = new TextView[]{
                findViewById(R.id.navHomeLabel),
                findViewById(R.id.navGroupsLabel),
                findViewById(R.id.navSettingsLabel)};

        homePage = new HomePage(this, pages[TAB_HOME]);
        groupsPage = new GroupsPage(this, pages[TAB_GROUPS]);
        settingsPage = new SettingsPage(this, pages[TAB_SETTINGS]);

        for (int i = 0; i < navItems.length; i++) {
            final int tab = i;
            navItems[i].setOnClickListener(v -> selectTab(tab));
        }

        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt(STATE_TAB, TAB_HOME);
            if (currentTab < TAB_HOME || currentTab > TAB_SETTINGS) currentTab = TAB_HOME;
        }
        showTab(currentTab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 權限狀態、群組內容可能在離開期間改變
        refreshCurrent();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    @Override
    public void onBackPressed() {
        if (currentTab != TAB_HOME) {
            selectTab(TAB_HOME);
        } else {
            super.onBackPressed();
        }
    }

    /** 切換分頁並重新整理該頁。 */
    void selectTab(int tab) {
        showTab(tab);
        refreshCurrent();
    }

    /** 首頁的空狀態按鈕：跳到群組頁並開啟「新增群組」。 */
    void startCreateGroup() {
        selectTab(TAB_GROUPS);
        groupsPage.showCreateDialog();
    }

    private void showTab(int tab) {
        currentTab = tab;
        int on = getColor(R.color.accent);
        int off = getColor(R.color.text_secondary);
        for (int i = 0; i < pages.length; i++) {
            boolean selected = i == tab;
            pages[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            navIcons[i].setImageTintList(ColorStateList.valueOf(selected ? on : off));
            navLabels[i].setTextColor(selected ? on : off);
            navItems[i].setSelected(selected);
        }
    }

    private void refreshCurrent() {
        switch (currentTab) {
            case TAB_GROUPS:
                groupsPage.refresh();
                break;
            case TAB_SETTINGS:
                settingsPage.refresh();
                break;
            default:
                homePage.refresh();
                break;
        }
    }
}
