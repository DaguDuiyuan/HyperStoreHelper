package com.dagu.hyperstorehelper;

/**
 * Runtime names in com.xiaomi.market 4.129.s.00.
 *
 * <p>JADX may rename conflicting packages to p108ui / p103ad in its output. Those
 * directory names are not present in the loaded DEX and must not be used here.</p>
 */
public final class Host {

    private Host() {
    }

    public static final String TAG = "HyperStore-AdFree";
    public static final String PKG = "com.xiaomi.market";

    public static final String CLASS_INSTRUMENTATION = "android.app.Instrumentation";
    public static final String METHOD_CALL_APPLICATION_ON_CREATE = "callApplicationOnCreate";

    public static final String CLASS_FRUIT_ENTRANCE =
            "com.xiaomi.market.ui.entrance.EntranceView";
    public static final String METHOD_FRUIT_BIND_VIEW = "bindView";

    public static final String CLASS_PAGER_TABS_INFO = "com.xiaomi.market.ui.PagerTabsInfo";
    public static final String CLASS_TAB_INFO = "com.xiaomi.market.model.TabInfo";
    public static final String METHOD_PAGER_TABS_FROM_TAB_INFO = "fromTabInfo";
    public static final String METHOD_TAB_GET_TAG = "getTag";

    public static final String CLASS_PAGE_CONFIG = "com.xiaomi.market.model.PageConfig";
    public static final String METHOD_PAGE_CONFIG_UPDATE_TABS = "updateTabs";

    public static final String CLASS_DOUBLE_TAB_PROXY_ACTIVITY_WRAPPER =
            "com.xiaomi.market.ui.DoubleTabProxyActivityWrapper";
    public static final String METHOD_DOUBLE_TAB_INIT_TAB = "initTab";
    public static final String METHOD_DOUBLE_TAB_ON_NEW_INTENT = "onNewIntent";
    public static final String METHOD_DOUBLE_TAB_ON_RESUME = "onResume";
    public static final String METHOD_DOUBLE_TAB_ON_TAB_SELECTED = "onTabSelected";

    public static final String CLASS_NATIVE_MINE_FRAGMENT =
            "com.xiaomi.market.business_ui.main.mine.NativeMinePagerFragment";
    public static final String METHOD_BIND_AD_FRAGMENT_VIEW = "bindAdFragmentView";

    public static final String CLASS_BASE_MARKET_PREFERENCE =
            "com.xiaomi.market.ui.BaseMarketPreference";
    public static final String METHOD_ON_CREATE_PREFERENCES = "onCreatePreferences";

    public static final String CLASS_MARKET_PREFERENCE_ACTIVITY =
            "com.xiaomi.market.ui.MarketPreferenceActivity";
    public static final String CLASS_ACTION_BAR = "miuix.appcompat.app.ActionBar";
    public static final String EXTRA_HELPER_PAGE =
            "com.dagu.hyperstorehelper.extra.HELPER_PAGE";
    public static final String FIELD_BASE_ACTION_BAR = "mActionBar";
    /** Obfuscated name in 4.129.s.00: ActionBar.setEndView(View). */
    public static final String METHOD_ACTION_BAR_SET_END_VIEW = "g0";

    public static final String CLASS_SPLASH_MANAGER =
            "com.xiaomi.market.ui.splash.SplashManager";
    public static final String CLASS_SPLASH_TASK =
            "com.xiaomi.market.ui.splash.SplashManager$SplashTask";
    public static final String CLASS_MARKET_TAB_ACTIVITY =
            "com.xiaomi.market.business_ui.main.MarketTabActivity";
    public static final String METHOD_SPLASH_GET_INSTANCE = "getInstance";
    public static final String METHOD_SPLASH_ON_END = "onSplashEnd";
    public static final String METHOD_SPLASH_TASK_RUN = "run";
    public static final String METHOD_SPLASH_TRY_AD = "tryAdSplash";
    public static final String METHOD_SPLASH_HIDE_COVER = "hideSplashCover";

    public static final String PARENT_HOME_TAB = "native_market_home";
    public static final String BOTTOM_GAME_TAB = "native_market_game";

    /** Actual server tag order, verified in assets/page_configuration. */
    public static final String[] ALLOWED_HOME_TOP_TABS = {
            "GOLDEN MI AWARD",
            "software_sub5",
            "Classification",
    };

    public static final String FIELD_PAGER_TAGS = "tags";
    public static final String FIELD_PAGER_TITLES = "titles";
    public static final String FIELD_PAGER_URLS = "urls";
    public static final String FIELD_PAGER_AB_NORMALS = "abNormals";
    public static final String FIELD_PAGER_TAB_INFOS = "tabInfos";
    public static final String FIELD_PAGER_DEFAULT_SELECTED_TAG = "defaultSelectedTag";
    public static final String[] FIELD_PAGER_ICON_MAPS = {
            "iconNormalUrls",
            "iconPressedUrls",
            "iconNormalDarkUrls",
            "iconPressedDarkUrls",
    };

    public static final String FIELD_PAGE_TABS = "tabs";
    public static final String FIELD_DEFAULT_BOTTOM_TAB_INDEX =
            "defaultSelectedBottomTabIndex";

    public static final String FIELD_PROXY_ACTIVITY = "mActivity";
    public static final String FIELD_TAB_PAGE_CONFIG = "mPageConfig";
    public static final String FIELD_REQUESTED_TAB = "mRequestedTab";
    public static final String FIELD_REQUESTED_SUB_TAB = "mRequestedSubTab";
    public static final String FIELD_SPLASH_TASK_ACTIVITY = "activityRef";

    /** Explicit routing extras must keep their original target on launcher-style intents. */
    public static final String[] ROUTING_EXTRAS = {
            "tag",
            "subTag",
            "extra_tab",
    };
}
