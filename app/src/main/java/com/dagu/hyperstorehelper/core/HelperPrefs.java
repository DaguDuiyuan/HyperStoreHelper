package com.dagu.hyperstorehelper.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dagu.hyperstorehelper.Host;

import io.github.libxposed.api.XposedModule;

/** Preferences stored inside the host app's private data directory. */
public final class HelperPrefs {

    private static final String PREFS_NAME = "hyper_store_helper";

    public static final String KEY_HOME_FRUIT_ENTRY = "helper_home_fruit_entry";
    public static final String KEY_HOME_TOP_TABS = "helper_home_top_tabs";
    public static final String KEY_BOTTOM_NAV_GAMES = "helper_bottom_nav_games";
    public static final String KEY_PROFILE_APP_RECOMMENDATIONS =
            "helper_profile_app_recommendations";
    public static final String KEY_STARTUP_ADS = "helper_startup_ads";
    public static final String KEY_GAME_RANK_ADS = "helper_game_rank_ads";
    public static final String KEY_DOWNLOAD_RECOMMENDATIONS =
            "helper_download_recommendations";

    public static final Feature[] FEATURES = {
            new Feature(
                    KEY_HOME_FRUIT_ENTRY,
                    "首页去除送水果入口",
                    "隐藏首页顶部的送水果 / 种果树活动入口"),
            new Feature(
                    KEY_HOME_TOP_TABS,
                    "首页顶部 Tab 白名单",
                    "仅保留金米奖、软件、分类，并按此顺序排列"),
            new Feature(
                    KEY_BOTTOM_NAV_GAMES,
                    "去除底部游戏导航并固定首页",
                    "移除底部「游戏」导航项；正常启动固定进入首页，不改变深链目标"),
            new Feature(
                    KEY_PROFILE_APP_RECOMMENDATIONS,
                    "去除我的页面应用推荐",
                    "不再创建「我的」页面中的应用推荐模块"),
            new Feature(
                    KEY_STARTUP_ADS,
                    "去除启动广告",
                    "跳过主页面冷启动和回到前台时的开屏广告，保留普通启动流程"),
            new Feature(
                    KEY_GAME_RANK_ADS,
                    "游戏榜去除广告排名",
                    "移除榜单游戏榜中的广告条目，并重新连续编号"),
            new Feature(
                    KEY_DOWNLOAD_RECOMMENDATIONS,
                    "下载页去除推荐区块",
                    "移除「最近安装完成」「大家还安装了」和「为你优选」"),
    };

    private static volatile Context sAppContext;

    private final XposedModule module;

    public HelperPrefs(XposedModule module) {
        this.module = module;
    }

    public static Context appContext() {
        return sAppContext;
    }

    public void attachApplication(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        sAppContext = app == null ? context : app;
    }

    private SharedPreferences prefs() {
        Context context = sAppContext;
        return context == null
                ? null
                : context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Missing values and unreadable preferences both mean enabled. */
    public boolean isEnabled(String key) {
        SharedPreferences preferences = prefs();
        return preferences == null || preferences.getBoolean(key, true);
    }

    public void write(String key, boolean value) {
        SharedPreferences preferences = prefs();
        if (preferences == null) {
            module.log(Log.WARN, Host.TAG, "cannot save " + key + ": no application context");
            return;
        }
        preferences.edit().putBoolean(key, value).apply();
        module.log(Log.INFO, Host.TAG, "setting " + key + "=" + value);
    }
}
