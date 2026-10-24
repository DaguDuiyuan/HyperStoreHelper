package com.dagu.hyperstorehelper.hooks;

import android.app.Activity;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F7: block startup splash-ad requests only for the market main activity. */
public final class StartupAdsHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();

    public StartupAdsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> splashManager = Reflect.loadClass(cl, Host.CLASS_SPLASH_MANAGER);
        Class<?> splashTask = Reflect.loadClass(cl, Host.CLASS_SPLASH_TASK);
        Method run = Reflect.findMethod(splashTask, Host.METHOD_SPLASH_TASK_RUN);
        Method getInstance = Reflect.findMethod(
                splashManager, Host.METHOD_SPLASH_GET_INSTANCE);
        Method onSplashEnd = Reflect.findMethod(
                splashManager,
                Host.METHOD_SPLASH_ON_END,
                Activity.class,
                String.class,
                int.class,
                boolean.class);
        if (run == null || getInstance == null || onSplashEnd == null) {
            logWarn(Host.CLASS_SPLASH_TASK
                    + ".run() or SplashManager cleanup methods not found");
            return;
        }
        hook(run).intercept(chain -> {
            Object activity = splashTaskActivity(chain.getThisObject());
            if (enabled(HelperPrefs.KEY_STARTUP_ADS) && isMarketMainActivity(activity)) {
                Activity mainActivity = (Activity) activity;
                if (!finishWithoutAd(mainActivity, getInstance, onSplashEnd)) {
                    return chain.proceed();
                }
                if (logged.compareAndSet(false, true)) {
                    logInfo("F7 startup splash ad suppressed for "
                            + mainActivity.getClass().getName());
                }
                return null;
            }
            return chain.proceed();
        });
    }

    private boolean finishWithoutAd(
            Activity activity, Method getInstance, Method onSplashEnd) {
        try {
            Object manager = getInstance.invoke(null);
            onSplashEnd.invoke(manager, activity, "", -1, false);
            return true;
        } catch (Throwable throwable) {
            logFailure("F7 finish canceled startup ad task failed", throwable);
            return false;
        }
    }

    private static Object splashTaskActivity(Object task) {
        Object value = Reflect.readField(task, Host.FIELD_SPLASH_TASK_ACTIVITY);
        return value instanceof WeakReference ? ((WeakReference<?>) value).get() : null;
    }

    private static boolean isMarketMainActivity(Object value) {
        if (!(value instanceof Activity)) {
            return false;
        }
        for (Class<?> type = value.getClass();
                type != null;
                type = type.getSuperclass()) {
            if (Host.CLASS_MARKET_TAB_ACTIVITY.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }
}
