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

/** F1: remove the home and app-detail fruit/orchard entrances. */
public final class FruitEntranceHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();
    private final AtomicBoolean loggedDetail = new AtomicBoolean();

    public FruitEntranceHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        installHomeEntranceHook(cl);
        installDetailOrchardHook(cl);
    }

    private void installHomeEntranceHook(ClassLoader cl) {
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

    private void installDetailOrchardHook(ClassLoader cl) {
        Class<?> controller = Reflect.loadClass(
                cl, Host.CLASS_DETAIL_ORCHARD_ICON_CONTROLLER);
        Class<?> detailType = Reflect.loadClass(cl, Host.CLASS_DETAIL_TYPE);
        Method shouldShowEntry = Reflect.findMethod(
                controller,
                Host.METHOD_DETAIL_ORCHARD_SHOULD_SHOW_ENTRY,
                boolean.class,
                detailType);
        if (shouldShowEntry != null) {
            hook(shouldShowEntry).intercept(chain -> {
                if (enabled(HelperPrefs.KEY_HOME_FRUIT_ENTRY)) {
                    if (loggedDetail.compareAndSet(false, true)) {
                        logInfo("F1 detail orchard entrance hidden");
                    }
                    return false;
                }
                return chain.proceed();
            });
            return;
        }

        Method refreshInternal = Reflect.findMethod(
                controller, Host.METHOD_DETAIL_ORCHARD_REFRESH_INTERNAL);
        if (refreshInternal == null) {
            logWarn(Host.CLASS_DETAIL_ORCHARD_ICON_CONTROLLER
                    + ".shouldShowEntry(boolean, DetailType) and refreshInternal() not found");
            return;
        }
        hook(refreshInternal).intercept(chain -> {
            Object result = chain.proceed();
            if (enabled(HelperPrefs.KEY_HOME_FRUIT_ENTRY)) {
                Object icon = Reflect.readField(
                        chain.getThisObject(), Host.FIELD_DETAIL_ORCHARD_ICON_VIEW);
                if (icon instanceof View) {
                    View view = (View) icon;
                    view.setVisibility(View.GONE);
                    view.setEnabled(false);
                    view.setClickable(false);
                    view.setOnClickListener(null);
                }
                if (loggedDetail.compareAndSet(false, true)) {
                    logInfo("F1 detail orchard entrance hidden via view fallback");
                }
            }
            return result;
        });
    }
}
