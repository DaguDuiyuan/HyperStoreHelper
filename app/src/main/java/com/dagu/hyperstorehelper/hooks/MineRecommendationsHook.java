package com.dagu.hyperstorehelper.hooks;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F4: never create the "mine" page recommendation container or fragment. */
public final class MineRecommendationsHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();

    public MineRecommendationsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> fragment = Reflect.loadClass(cl, Host.CLASS_NATIVE_MINE_FRAGMENT);
        Method bindAdFragmentView = Reflect.findMethod(
                fragment, Host.METHOD_BIND_AD_FRAGMENT_VIEW, int.class);
        if (bindAdFragmentView == null) {
            logWarn(Host.CLASS_NATIVE_MINE_FRAGMENT
                    + ".bindAdFragmentView(int) not found");
            return;
        }
        hook(bindAdFragmentView).intercept(chain -> {
            if (enabled(HelperPrefs.KEY_PROFILE_APP_RECOMMENDATIONS)) {
                if (logged.compareAndSet(false, true)) {
                    logInfo("F4 mine application recommendations suppressed");
                }
                return null;
            }
            return chain.proceed();
        });
    }
}
