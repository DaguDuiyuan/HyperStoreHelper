package com.dagu.hyperstorehelper.hooks;

import android.app.Application;
import android.content.Context;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * Capture the host Application before feature hooks need to read switches.
 * Instrumentation.callApplicationOnCreate is on the normal app startup path.
 */
public final class AppContextHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();

    public AppContextHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> instrumentation = Reflect.loadClass(cl, Host.CLASS_INSTRUMENTATION);
        Method method = Reflect.findMethod(
                instrumentation,
                Host.METHOD_CALL_APPLICATION_ON_CREATE,
                Application.class);
        if (method == null) {
            logWarn("Instrumentation.callApplicationOnCreate(Application) not found");
            return;
        }
        hook(method).intercept(chain -> {
            Object result = chain.proceed();
            Object application = chain.getArg(0);
            if (application instanceof Context) {
                prefs.attachApplication((Context) application);
                if (logged.compareAndSet(false, true)) {
                    logInfo("host application context captured");
                }
            }
            return result;
        });
    }
}
