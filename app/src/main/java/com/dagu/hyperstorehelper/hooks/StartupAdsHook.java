package com.dagu.hyperstorehelper.hooks;

import android.app.Activity;
import android.content.Context;
import android.view.View;

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
        Method tryAdSplash = Reflect.findMethod(
                splashManager,
                Host.METHOD_SPLASH_TRY_AD,
                Activity.class,
                String.class);
        Method getInstance = Reflect.findMethod(
                splashManager, Host.METHOD_SPLASH_GET_INSTANCE);
        Method hideSplashCover = Reflect.findMethod(
                splashManager,
                Host.METHOD_SPLASH_HIDE_COVER,
                Context.class,
                boolean.class);
        if (hideSplashCover == null) {
            hideSplashCover = Reflect.findMethod(
                    splashManager,
                    Host.METHOD_SPLASH_HIDE_COVER,
                    Activity.class,
                    boolean.class);
        }

        if (tryAdSplash == null) {
            logWarn(Host.CLASS_SPLASH_MANAGER + ".tryAdSplash(Activity, String) not found");
        } else if (getInstance == null || hideSplashCover == null) {
            logWarn(Host.CLASS_SPLASH_MANAGER
                    + ".getInstance()/hideSplashCover(Context, boolean) not found");
        } else {
            Method managerGetter = getInstance;
            Method hideCover = hideSplashCover;
            hook(tryAdSplash).intercept(chain -> {
                Object activity = chain.getArg(0);
                if (!enabled(HelperPrefs.KEY_STARTUP_ADS)
                        || !isMarketMainActivity(activity)) {
                    return chain.proceed();
                }
                scheduleCleanup((Activity) activity, managerGetter, hideCover);
                if (logged.compareAndSet(false, true)) {
                    logInfo("F7 startup splash ad skipped before request for "
                            + activity.getClass().getName());
                }
                return null;
            });
        }

        Method run = Reflect.findMethod(splashTask, Host.METHOD_SPLASH_TASK_RUN);
        if (run == null) {
            logWarn(Host.CLASS_SPLASH_TASK + ".run() not found");
            return;
        }
        if (getInstance == null || hideSplashCover == null) {
            return;
        }
        Method managerGetter = getInstance;
        Method hideCover = hideSplashCover;
        hook(run).intercept(chain -> {
            Object activity = splashTaskActivity(chain.getThisObject());
            if (enabled(HelperPrefs.KEY_STARTUP_ADS) && isMarketMainActivity(activity)) {
                scheduleCleanup((Activity) activity, managerGetter, hideCover);
                if (logged.compareAndSet(false, true)) {
                    logInfo("F7 startup splash ad task suppressed for "
                            + activity.getClass().getName());
                }
                return null;
            }
            return chain.proceed();
        });
    }

    /**
     * The host replaces its temporary splash cover as soon as the ad task ends.
     * Run the same cleanup after the activity has drawn its first frame so the
     * skip is not defeated by onSplashEnd's ActivityMonitor guard during cold start.
     */
    private void scheduleCleanup(
            Activity activity, Method getInstance, Method hideSplashCover) {
        Runnable cleanup = () -> {
            try {
                Object manager = getInstance.invoke(null);
                if (manager == null) {
                    logWarn("F7 SplashManager instance unavailable during cleanup");
                    return;
                }
                hideSplashCover.invoke(manager, activity, false);
            } catch (Throwable throwable) {
                logFailure("F7 dismiss temporary splash cover failed", throwable);
            }
        };
        try {
            View decorView = activity.getWindow().getDecorView();
            decorView.postOnAnimation(cleanup);
            // Retry once for slow cold starts where the first frame is not resumed yet.
            decorView.postDelayed(cleanup, 120L);
        } catch (Throwable throwable) {
            logFailure("F7 schedule splash cleanup failed", throwable);
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
