package com.dagu.hyperstorehelper.hooks;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Preload the mine page through the host's existing idle preload path. */
public final class MinePagePreloadHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();
    private final AtomicBoolean warnedMissingField = new AtomicBoolean();

    public MinePagePreloadHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> layout = Reflect.loadClass(cl, Host.CLASS_BOTTOM_TAB_LAYOUT_MIUX4);
        Method addFragmentTab = Reflect.findMethodByName(
                layout, Host.METHOD_BOTTOM_TAB_ADD_FRAGMENT);
        if (addFragmentTab == null) {
            logWarn(Host.CLASS_BOTTOM_TAB_LAYOUT_MIUX4
                    + "." + Host.METHOD_BOTTOM_TAB_ADD_FRAGMENT + " not found");
            return;
        }
        hook(addFragmentTab).intercept(chain -> {
            Object tab = chain.getArg(0);
            if (tab != null && Host.PARENT_MINE_TAB.equals(tagOf(tab))) {
                if (Reflect.writeField(tab, Host.FIELD_TAB_NEED_PRELOAD, true)) {
                    if (logged.compareAndSet(false, true)) {
                        logInfo("mine tab preload enabled before fragment registration");
                    }
                } else if (warnedMissingField.compareAndSet(false, true)) {
                    logWarn("TabInfo." + Host.FIELD_TAB_NEED_PRELOAD
                            + " not writable; mine tab stays lazily created");
                }
            }
            return chain.proceed();
        });
    }

    private static String tagOf(Object tab) {
        try {
            Method getTag = tab.getClass().getMethod(Host.METHOD_TAB_GET_TAG);
            Object value = getTag.invoke(tab);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
