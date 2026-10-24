package com.dagu.hyperstorehelper.hooks;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F10 (default off): hide only the bottom navigation rank tab. */
public final class HideRankTabHook extends BaseHook {

    private final AtomicBoolean loggedObserved = new AtomicBoolean();

    public HideRankTabHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> pageConfig = Reflect.loadClass(cl, Host.CLASS_PAGE_CONFIG);
        Method updateTabs = Reflect.findMethod(
                pageConfig, Host.METHOD_PAGE_CONFIG_UPDATE_TABS, boolean.class);
        if (pageConfig == null || updateTabs == null) {
            logWarn(Host.CLASS_PAGE_CONFIG + ".updateTabs(boolean) not found");
            return;
        }
        hook(updateTabs).intercept(chain -> {
            Object result = chain.proceed();
            if (prefs.isEnabled(HelperPrefs.KEY_HIDE_RANK_TAB, false)) {
                try {
                    filterBottomTabs(chain.getThisObject(), pageConfig);
                } catch (Throwable throwable) {
                    logFailure("F10 hide rank tab failed", throwable);
                }
            }
            return result;
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void filterBottomTabs(Object pageConfigInstance, Class<?> pageConfigClass)
            throws Exception {
        Object tabsValue = Reflect.readField(pageConfigInstance, Host.FIELD_PAGE_TABS);
        if (!(tabsValue instanceof List)) {
            logWarn("F10 PageConfig.tabs is not a List; skipping");
            return;
        }
        List<?> original = (List<?>) tabsValue;
        if (loggedObserved.compareAndSet(false, true)) {
            logInfo("F10 bottom tabs before filter = " + tags(original));
        }

        Object selectedIndexValue = Reflect.readStaticField(
                pageConfigClass, Host.FIELD_DEFAULT_BOTTOM_TAB_INDEX);
        int selectedIndex = selectedIndexValue instanceof Number
                ? ((Number) selectedIndexValue).intValue() : 0;
        String selectedTag = tagAt(original, selectedIndex);

        List<Object> filtered = new ArrayList<>(original.size());
        boolean removed = false;
        for (Object tab : original) {
            if (Host.PARENT_RANK_TAB.equals(tagOf(tab))) {
                removed = true;
            } else {
                filtered.add(tab);
            }
        }

        if (removed) {
            Reflect.writeField(pageConfigInstance, Host.FIELD_PAGE_TABS, filtered);
        }

        int newSelectedIndex = indexOfTag(filtered, selectedTag);
        if (newSelectedIndex < 0) {
            newSelectedIndex = homeIndex(filtered);
        }
        if (newSelectedIndex < 0) {
            newSelectedIndex = 0;
        }
        Reflect.writeStaticField(
                pageConfigClass, Host.FIELD_DEFAULT_BOTTOM_TAB_INDEX, newSelectedIndex);

        if (removed) {
            logInfo("F10 rank tab removed, tabs=" + tags(filtered)
                    + ", selectedIndex=" + newSelectedIndex);
        }
    }

    private static List<String> tags(List<?> tabs) throws Exception {
        if (tabs == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(tabs.size());
        for (Object tab : tabs) {
            result.add(tagOf(tab));
        }
        return result;
    }

    private static String tagAt(List<?> tabs, int index) throws Exception {
        return tabs == null || index < 0 || index >= tabs.size()
                ? null : tagOf(tabs.get(index));
    }

    private static String tagOf(Object tabInfo) throws Exception {
        if (tabInfo == null) {
            return null;
        }
        Method getTag = tabInfo.getClass().getMethod(Host.METHOD_TAB_GET_TAG);
        Object value = getTag.invoke(tabInfo);
        return value instanceof String ? (String) value : null;
    }

    private static int indexOfTag(List<?> tabs, String tag) throws Exception {
        if (tag == null) {
            return -1;
        }
        for (int i = 0; i < tabs.size(); i++) {
            if (tag.equals(tagOf(tabs.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static int homeIndex(List<?> tabs) throws Exception {
        for (int i = 0; i < tabs.size(); i++) {
            String tag = tagOf(tabs.get(i));
            if (Host.PARENT_HOME_TAB.equals(tag) || "home".equals(tag)) {
                return i;
            }
        }
        return -1;
    }
}
