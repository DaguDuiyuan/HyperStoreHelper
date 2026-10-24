package com.dagu.hyperstorehelper.core;

import android.util.Log;

import com.dagu.hyperstorehelper.Host;

import java.lang.reflect.Executable;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Shared logging, preference lookup and hook installation for one feature group. */
public abstract class BaseHook {

    protected final XposedModule module;
    protected final HelperPrefs prefs;

    protected BaseHook(XposedModule module, HelperPrefs prefs) {
        this.module = module;
        this.prefs = prefs;
    }

    public abstract void install(ClassLoader cl);

    protected XposedInterface.HookBuilder hook(Executable executable) {
        return module.hook(executable);
    }

    protected boolean enabled(String key) {
        return prefs.isEnabled(key);
    }

    public void logInfo(String message) {
        module.log(Log.INFO, Host.TAG, message);
    }

    public void logWarn(String message) {
        module.log(Log.WARN, Host.TAG, message);
    }

    public void logFailure(String what, Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        StackTraceElement[] frames = cause.getStackTrace();
        logWarn(what + ": " + cause + (frames.length > 0 ? " @ " + frames[0] : ""));
    }
}
