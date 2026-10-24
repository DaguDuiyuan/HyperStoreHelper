package com.dagu.hyperstorehelper.hooks;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F12: render every available update instead of the folded preview. */
public final class UpdateAutoExpandHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();
    private final AtomicBoolean loggedStateMissing = new AtomicBoolean();
    private final AtomicBoolean loggedV1 = new AtomicBoolean();
    private final AtomicBoolean loggedV1StateMissing = new AtomicBoolean();

    public UpdateAutoExpandHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        installV2(cl);
        installV1(cl);
    }

    private void installV2(ClassLoader cl) {
        Class<?> managerClass = Reflect.loadClass(cl, Host.CLASS_UPDATE_COMPONENT_MANAGER);
        Class<?> uiContextClass = Reflect.loadClass(cl, Host.CLASS_UI_CONTEXT);
        Class<?> stateClass = Reflect.loadClass(cl, Host.CLASS_UPDATE_PAGE_COLLAPSE_STATE);
        Method buildComponentList = Reflect.findMethod(
                managerClass,
                Host.METHOD_UPDATE_BUILD_COMPONENT_LIST,
                ArrayList.class,
                uiContextClass);
        if (buildComponentList == null) {
            buildComponentList = Reflect.findMethodByName(
                    managerClass, Host.METHOD_UPDATE_BUILD_COMPONENT_LIST);
        }
        if (buildComponentList == null) {
            logWarn(Host.CLASS_UPDATE_COMPONENT_MANAGER
                    + ".buildComponentList(ArrayList, UIContext) not found");
            return;
        }

        Method target = buildComponentList;
        hook(target).intercept(chain -> {
            if (enabled(HelperPrefs.KEY_UPDATE_AUTO_EXPAND)) {
                Object expanded = Reflect.readStaticField(
                        stateClass, Host.UPDATE_PAGE_COLLAPSE_EXPAND);
                if (expanded == null) {
                    if (loggedStateMissing.compareAndSet(false, true)) {
                        logWarn("F12 PageCollapseState.Expand not found");
                    }
                } else if (!Reflect.writeField(
                        chain.getThisObject(),
                        Host.FIELD_UPDATE_PAGE_COLLAPSE_STATE,
                        expanded)) {
                    if (loggedStateMissing.compareAndSet(false, true)) {
                        logWarn("F12 UpdateComponentManager.pageCollapseState not found");
                    }
                } else if (logged.compareAndSet(false, true)) {
                    logInfo("F12 update list forced to expanded state");
                }
            }
            return chain.proceed();
        });
    }

    private void installV1(ClassLoader cl) {
        Class<?> adapterClass = Reflect.loadClass(cl, Host.CLASS_UPDATE_LIST_RV_ADAPTER);
        Class<?> stateClass = Reflect.loadClass(cl, Host.CLASS_UPDATE_RV_PAGE_COLLAPSE_STATE);
        Method updateData = Reflect.findMethod(
                adapterClass, Host.METHOD_UPDATE_RV_UPDATE_DATA, List.class);
        if (updateData == null) {
            logWarn(Host.CLASS_UPDATE_LIST_RV_ADAPTER + ".updateData(List) not found");
            return;
        }
        hook(updateData).intercept(chain -> {
            if (enabled(HelperPrefs.KEY_UPDATE_AUTO_EXPAND)) {
                Object expanded = Reflect.readStaticField(
                        stateClass, Host.UPDATE_PAGE_COLLAPSE_EXPAND);
                if (expanded == null) {
                    if (loggedV1StateMissing.compareAndSet(false, true)) {
                        logWarn("F12 UpdateListRvAdapter PageCollapseState.Expand not found");
                    }
                } else if (!Reflect.writeField(
                        chain.getThisObject(),
                        Host.FIELD_UPDATE_RV_PAGE_COLLAPSE_STATE,
                        expanded)) {
                    if (loggedV1StateMissing.compareAndSet(false, true)) {
                        logWarn("F12 UpdateListRvAdapter.pageCollapseState not found");
                    }
                } else if (loggedV1.compareAndSet(false, true)) {
                    logInfo("F12 legacy update list forced to expanded state");
                }
            }
            return chain.proceed();
        });
    }
}
