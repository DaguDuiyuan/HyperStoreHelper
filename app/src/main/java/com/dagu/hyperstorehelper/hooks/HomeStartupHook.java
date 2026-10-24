package com.dagu.hyperstorehelper.hooks;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F3 companion: normal launcher starts and page rebuilds select the home bottom tab. */
public final class HomeStartupHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();

    public HomeStartupHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> wrapper = Reflect.loadClass(
                cl, Host.CLASS_DOUBLE_TAB_PROXY_ACTIVITY_WRAPPER);
        Class<?> pageConfig = Reflect.loadClass(cl, Host.CLASS_PAGE_CONFIG);
        Method initTab = Reflect.findMethod(wrapper, Host.METHOD_DOUBLE_TAB_INIT_TAB);
        Method onNewIntent = Reflect.findMethod(
                wrapper, Host.METHOD_DOUBLE_TAB_ON_NEW_INTENT, Intent.class);

        if (initTab == null) {
            logWarn(Host.CLASS_DOUBLE_TAB_PROXY_ACTIVITY_WRAPPER
                    + ".initTab() not found");
        } else {
            hook(initTab).intercept(chain -> {
                forceHomeOnLauncherIntent(chain.getThisObject(), pageConfig, "startup");
                return chain.proceed();
            });
        }

        if (onNewIntent == null) {
            logWarn(Host.CLASS_DOUBLE_TAB_PROXY_ACTIVITY_WRAPPER
                    + ".onNewIntent(Intent) not found");
        } else {
            hook(onNewIntent).intercept(chain -> {
                Object intent = chain.getArg(0);
                if (intent instanceof Intent
                        && forceHome(chain.getThisObject(), pageConfig,
                        (Intent) intent, "launcher re-entry")) {
                    // DoubleTabProxyActivityWrapper uses the fields in chain.proceed().
                }
                return chain.proceed();
            });
        }
    }

    private void forceHomeOnLauncherIntent(
            Object wrapper, Class<?> pageConfigClass, String reason) {
        if (!shouldForceHome()) {
            return;
        }
        try {
            Object activityValue = Reflect.readField(wrapper, Host.FIELD_PROXY_ACTIVITY);
            if (!(activityValue instanceof Activity)) {
                logWarn("F3 proxy activity not found; keeping host selection");
                return;
            }
            Intent intent = ((Activity) activityValue).getIntent();
            forceHome(wrapper, pageConfigClass, intent, reason);
        } catch (Throwable throwable) {
            logFailure("F3 force home on startup failed", throwable);
        }
    }

    private boolean forceHome(
            Object wrapper, Class<?> pageConfigClass, Intent intent, String reason) {
        if (!shouldForceHome() || !isNormalLauncherIntent(intent)) {
            return false;
        }
        try {
            int homeIndex = findHomeIndex(wrapper);
            if (homeIndex < 0) {
                logWarn("F3 home tab not found; keeping host selection");
                return false;
            }
            boolean tabWritten = Reflect.writeField(
                    wrapper, Host.FIELD_REQUESTED_TAB, homeIndex);
            boolean subTabWritten = Reflect.writeField(
                    wrapper, Host.FIELD_REQUESTED_SUB_TAB, -1);
            boolean defaultWritten = Reflect.writeStaticField(
                    pageConfigClass,
                    Host.FIELD_DEFAULT_BOTTOM_TAB_INDEX,
                    homeIndex);
            if (!tabWritten || !subTabWritten || !defaultWritten) {
                logWarn("F3 could not update all tab selection fields; keeping host selection");
                return false;
            }
            if (logged.compareAndSet(false, true)) {
                logInfo("F3 " + reason + " selected home index=" + homeIndex);
            }
            return true;
        } catch (Throwable throwable) {
            logFailure("F3 force home failed", throwable);
            return false;
        }
    }

    private boolean shouldForceHome() {
        return enabled(HelperPrefs.KEY_BOTTOM_NAV_GAMES)
                || prefs.isEnabled(HelperPrefs.KEY_HIDE_RANK_TAB, false);
    }

    private static boolean isNormalLauncherIntent(Intent intent) {
        if (intent == null
                || !Intent.ACTION_MAIN.equals(intent.getAction())
                || !intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                || intent.getData() != null) {
            return false;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return true;
        }
        for (String key : Host.ROUTING_EXTRAS) {
            if (extras.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    private static int findHomeIndex(Object wrapper) throws Exception {
        Object pageConfig = Reflect.readField(wrapper, Host.FIELD_TAB_PAGE_CONFIG);
        Object tabsValue = Reflect.readField(pageConfig, Host.FIELD_PAGE_TABS);
        if (!(tabsValue instanceof List)) {
            return -1;
        }
        List<?> tabs = (List<?>) tabsValue;
        for (int i = 0; i < tabs.size(); i++) {
            Object tab = tabs.get(i);
            if (tab == null) {
                continue;
            }
            Method getTag = tab.getClass().getMethod(Host.METHOD_TAB_GET_TAG);
            Object tag = getTag.invoke(tab);
            if (Host.PARENT_HOME_TAB.equals(tag) || "home".equals(tag)) {
                return i;
            }
        }
        return -1;
    }
}
