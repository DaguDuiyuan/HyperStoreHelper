package com.dagu.hyperstorehelper.hooks;

import android.content.Context;
import android.view.View;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F1: remove the home toolbar fruit/orchard entrance. */
public final class FruitEntranceHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();

    public FruitEntranceHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> entranceView = Reflect.loadClass(cl, Host.CLASS_FRUIT_ENTRANCE);
        Method bindView = Reflect.findMethod(
                entranceView,
                Host.METHOD_FRUIT_BIND_VIEW,
                Context.class,
                String.class);
        if (bindView == null) {
            logWarn(Host.CLASS_FRUIT_ENTRANCE + ".bindView(Context, String) not found");
            return;
        }
        hook(bindView).intercept(chain -> {
            Object result = chain.proceed();
            if (enabled(HelperPrefs.KEY_HOME_FRUIT_ENTRY)) {
                Object target = chain.getThisObject();
                if (target instanceof View) {
                    View view = (View) target;
                    view.setVisibility(View.GONE);
                    view.setEnabled(false);
                    view.setClickable(false);
                    view.setFocusable(false);
                    view.setOnClickListener(null);
                    view.setOnLongClickListener(null);
                    view.setOnTouchListener(null);
                    if (logged.compareAndSet(false, true)) {
                        logInfo("F1 fruit entrance hidden");
                    }
                }
            }
            return result;
        });
    }
}
