package com.dagu.hyperstorehelper.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F6: keep status bar icon contrast in sync with the host's actual theme. */
public final class StatusBarIconsHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();

    public StatusBarIconsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> wrapper = Reflect.loadClass(
                cl, Host.CLASS_DOUBLE_TAB_PROXY_ACTIVITY_WRAPPER);
        Class<?> uiUtils = Reflect.loadClass(cl, Host.CLASS_UI_UTILS);
        Class<?> client = Reflect.loadClass(cl, Host.CLASS_CLIENT);

        Method onResume = Reflect.findMethod(wrapper, Host.METHOD_DOUBLE_TAB_ON_RESUME);
        Method onNewIntent = Reflect.findMethod(
                wrapper, Host.METHOD_DOUBLE_TAB_ON_NEW_INTENT, Intent.class);
        Method onTabSelected = Reflect.findMethod(
                wrapper, Host.METHOD_DOUBLE_TAB_ON_TAB_SELECTED, int.class, int.class);
        Method isEnableDarkMode = Reflect.findMethod(
                client, Host.METHOD_CLIENT_IS_ENABLE_DARK_MODE, Context.class);
        Method setStatusBarDarkMode = findStatusBarSetter(uiUtils);

        if (setStatusBarDarkMode == null || isEnableDarkMode == null) {
            logWarn("F6 status bar theme methods not found; skipping");
            return;
        }

        if (onResume == null) {
            logWarn(Host.CLASS_DOUBLE_TAB_PROXY_ACTIVITY_WRAPPER
                    + ".onResume() not found");
        } else {
            hook(onResume).intercept(chain -> {
                Object result = chain.proceed();
                applyNow(chain.getThisObject(), isEnableDarkMode, setStatusBarDarkMode);
                return result;
            });
        }

        if (onNewIntent == null) {
            logWarn(Host.CLASS_DOUBLE_TAB_PROXY_ACTIVITY_WRAPPER
                    + ".onNewIntent(Intent) not found");
        } else {
            hook(onNewIntent).intercept(chain -> {
                Object result = chain.proceed();
                applyLater(chain.getThisObject(), isEnableDarkMode, setStatusBarDarkMode);
                return result;
            });
        }

        if (onTabSelected == null) {
            logWarn(Host.CLASS_DOUBLE_TAB_PROXY_ACTIVITY_WRAPPER
                    + ".onTabSelected(int, int) not found");
        } else {
            hook(onTabSelected).intercept(chain -> {
                Object result = chain.proceed();
                applyLater(chain.getThisObject(), isEnableDarkMode, setStatusBarDarkMode);
                return result;
            });
        }
    }

    private static Method findStatusBarSetter(Class<?> uiUtils) {
        Method method = Reflect.findMethod(
                uiUtils,
                Host.METHOD_SET_STATUS_BAR_DARK_MODE_V2,
                Activity.class,
                boolean.class);
        return method != null ? method : Reflect.findMethod(
                uiUtils,
                Host.METHOD_SET_STATUS_BAR_DARK_MODE,
                Activity.class,
                boolean.class);
    }

    private void applyLater(
            Object wrapper, Method isEnableDarkMode, Method setStatusBarDarkMode) {
        if (!enabled(HelperPrefs.KEY_STATUS_BAR_ICONS)) {
            return;
        }
        Activity activity = activityOf(wrapper);
        if (activity == null) {
            return;
        }
        activity.getWindow().getDecorView().post(
                () -> apply(activity, isEnableDarkMode, setStatusBarDarkMode));
    }

    private void applyNow(
            Object wrapper, Method isEnableDarkMode, Method setStatusBarDarkMode) {
        if (!enabled(HelperPrefs.KEY_STATUS_BAR_ICONS)) {
            return;
        }
        Activity activity = activityOf(wrapper);
        if (activity != null) {
            apply(activity, isEnableDarkMode, setStatusBarDarkMode);
        }
    }

    private void apply(
            Activity activity, Method isEnableDarkMode, Method setStatusBarDarkMode) {
        if (!enabled(HelperPrefs.KEY_STATUS_BAR_ICONS)) {
            return;
        }
        try {
            Object darkValue = isEnableDarkMode.invoke(null, activity);
            if (!(darkValue instanceof Boolean)) {
                return;
            }
            boolean dark = (Boolean) darkValue;
            setStatusBarDarkMode.invoke(null, activity, dark);
            if (logged.compareAndSet(false, true)) {
                logInfo("F6 status bar icons follow host darkMode=" + dark);
            }
        } catch (Throwable throwable) {
            logFailure("F6 update status bar icons failed", throwable);
        }
    }

    private static Activity activityOf(Object wrapper) {
        Object value = Reflect.readField(wrapper, Host.FIELD_PROXY_ACTIVITY);
        return value instanceof Activity ? (Activity) value : null;
    }
}
