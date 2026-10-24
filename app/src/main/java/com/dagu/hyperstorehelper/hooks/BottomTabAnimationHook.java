package com.dagu.hyperstorehelper.hooks;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * Keep the user-click path animated.
 *
 * <p>The host's BottomTabLayoutMiux4.selectTab() calls
 * BottomNavigator.v0(index, false), which uses OverlayView.setTo() and snaps
 * the indicator to its destination. The navigator's own click path uses true
 * and runs the normal spring animation. A thread-local click scope lets us
 * restore the animated argument only for real bottom-tab clicks.</p>
 */
public final class BottomTabAnimationHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();
    private final ThreadLocal<Boolean> userClickSwitch = new ThreadLocal<>();

    public BottomTabAnimationHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> layout = Reflect.loadClass(cl, Host.CLASS_BOTTOM_TAB_LAYOUT_MIUX4);
        Class<?> navigator = Reflect.loadClass(cl, Host.CLASS_BOTTOM_NAVIGATOR);
        Method performSwitch = Reflect.findMethod(
                layout, Host.METHOD_BOTTOM_TAB_PERFORM_SWITCH, int.class);
        Method select = Reflect.findMethod(
                navigator, Host.METHOD_BOTTOM_NAVIGATOR_SELECT, int.class, boolean.class);

        if (performSwitch == null) {
            logWarn(Host.CLASS_BOTTOM_TAB_LAYOUT_MIUX4
                    + "." + Host.METHOD_BOTTOM_TAB_PERFORM_SWITCH + "(int) not found");
        } else {
            hook(performSwitch).intercept(chain -> {
                userClickSwitch.set(Boolean.TRUE);
                try {
                    return chain.proceed();
                } finally {
                    userClickSwitch.remove();
                }
            });
        }

        if (select == null) {
            logWarn(Host.CLASS_BOTTOM_NAVIGATOR
                    + "." + Host.METHOD_BOTTOM_NAVIGATOR_SELECT
                    + "(int, boolean) not found");
            return;
        }
        hook(select).intercept(chain -> {
            Object animated = chain.getArg(1);
            if (Boolean.TRUE.equals(userClickSwitch.get())
                    && Boolean.FALSE.equals(animated)) {
                if (logged.compareAndSet(false, true)) {
                    logInfo("bottom tab click now uses the original spring animation");
                }
                return chain.proceed(new Object[]{chain.getArg(0), Boolean.TRUE});
            }
            return chain.proceed();
        });
    }
}
