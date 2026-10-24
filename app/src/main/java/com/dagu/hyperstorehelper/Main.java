package com.dagu.hyperstorehelper;

import android.annotation.SuppressLint;
import android.util.Log;

import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;
import com.dagu.hyperstorehelper.hooks.AppContextHook;
import com.dagu.hyperstorehelper.hooks.BottomNavGamesHook;
import com.dagu.hyperstorehelper.hooks.FruitEntranceHook;
import com.dagu.hyperstorehelper.hooks.HomeTopTabsHook;
import com.dagu.hyperstorehelper.hooks.HomeStartupHook;
import com.dagu.hyperstorehelper.hooks.MineRecommendationsHook;
import com.dagu.hyperstorehelper.hooks.StartupAdsHook;
import com.dagu.hyperstorehelper.hooks.StatusBarIconsHook;
import com.dagu.hyperstorehelper.settings.SettingsHook;

import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** LSPosed entry point. Each feature installs independently. */
public final class Main extends XposedModule {

    public Main() {
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, Host.TAG, "module loaded: process=" + param.getProcessName());
    }

    @Override
    @SuppressLint("NewApi") // LSPosed provides PackageLoadedParam on every supported API.
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!Host.PKG.equals(param.getPackageName())) {
            return;
        }
        ClassLoader classLoader = param.getDefaultClassLoader();
        HelperPrefs prefs = new HelperPrefs(this);
        log(Log.INFO, Host.TAG, "installing hooks for " + Host.PKG);
        for (BaseHook hook : hooks(prefs)) {
            try {
                hook.install(classLoader);
            } catch (Throwable throwable) {
                log(Log.WARN, Host.TAG,
                        hook.getClass().getSimpleName() + " install failed: " + throwable);
            }
        }
    }

    private List<BaseHook> hooks(HelperPrefs prefs) {
        List<BaseHook> hooks = new ArrayList<>();
        hooks.add(new AppContextHook(this, prefs));
        hooks.add(new FruitEntranceHook(this, prefs));
        hooks.add(new HomeTopTabsHook(this, prefs));
        hooks.add(new BottomNavGamesHook(this, prefs));
        hooks.add(new MineRecommendationsHook(this, prefs));
        hooks.add(new HomeStartupHook(this, prefs));
        hooks.add(new StatusBarIconsHook(this, prefs));
        hooks.add(new StartupAdsHook(this, prefs));
        hooks.add(new SettingsHook(this, prefs));
        return hooks;
    }
}
