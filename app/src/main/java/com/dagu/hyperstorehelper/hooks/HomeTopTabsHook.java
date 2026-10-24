package com.dagu.hyperstorehelper.hooks;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/** F2: filter and reorder the home parent Tab's sub-tabs. */
public final class HomeTopTabsHook extends BaseHook {

    public HomeTopTabsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> pagerTabsInfo = Reflect.loadClass(cl, Host.CLASS_PAGER_TABS_INFO);
        Class<?> tabInfo = Reflect.loadClass(cl, Host.CLASS_TAB_INFO);
        Method fromTabInfo = Reflect.findMethod(
                pagerTabsInfo,
                Host.METHOD_PAGER_TABS_FROM_TAB_INFO,
                tabInfo);
        if (fromTabInfo == null) {
            logWarn(Host.CLASS_PAGER_TABS_INFO + ".fromTabInfo(TabInfo) not found");
            return;
        }
        hook(fromTabInfo).intercept(chain -> {
            Object result = chain.proceed();
            if (result == null || !enabled(HelperPrefs.KEY_HOME_TOP_TABS)) {
                return result;
            }
            try {
                Object parentTab = chain.getArg(0);
                Object parentTag = readTag(parentTab);
                if (Host.PARENT_HOME_TAB.equals(parentTag)) {
                    filter(result);
                }
            } catch (Throwable throwable) {
                logFailure("F2 filter home top tabs failed", throwable);
            }
            return result;
        });
    }

    private static Object readTag(Object tabInfo) throws Exception {
        if (tabInfo == null) {
            return null;
        }
        Method getTag = tabInfo.getClass().getMethod(Host.METHOD_TAB_GET_TAG);
        return getTag.invoke(tabInfo);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void filter(Object pagerTabs) {
        Object tagsValue = Reflect.readField(pagerTabs, Host.FIELD_PAGER_TAGS);
        if (!(tagsValue instanceof List)) {
            logWarn("F2 PagerTabsInfo.tags is not a List; skipping");
            return;
        }

        List<?> originalTags = (List<?>) tagsValue;
        List<Integer> indices = selectedIndices(originalTags);
        if (indices.isEmpty()) {
            logWarn("F2 no allowed home top tabs in server data; skipping");
            return;
        }

        List<String> selectedTags = new ArrayList<>(indices.size());
        for (Integer index : indices) {
            selectedTags.add(String.valueOf(originalTags.get(index)));
        }

        List<?> titles = asList(Reflect.readField(pagerTabs, Host.FIELD_PAGER_TITLES));
        List<?> urls = asList(Reflect.readField(pagerTabs, Host.FIELD_PAGER_URLS));
        List<?> abNormals = asList(Reflect.readField(pagerTabs, Host.FIELD_PAGER_AB_NORMALS));

        List<Object> filteredTitles = select(titles, indices, Collections.emptyMap());
        List<Object> filteredUrls = select(urls, indices, "");
        List<Object> filteredAbNormals = select(abNormals, indices, Boolean.FALSE);

        Reflect.writeField(pagerTabs, Host.FIELD_PAGER_TAGS, new ArrayList<>(selectedTags));
        Reflect.writeField(pagerTabs, Host.FIELD_PAGER_TITLES, filteredTitles);
        Reflect.writeField(pagerTabs, Host.FIELD_PAGER_URLS, filteredUrls);
        Reflect.writeField(pagerTabs, Host.FIELD_PAGER_AB_NORMALS, filteredAbNormals);

        Set<String> allowed = new HashSet<>(selectedTags);
        rewriteTabInfoMap(pagerTabs, selectedTags, allowed);
        retainIconMaps(pagerTabs, allowed);

        Object defaultTag = Reflect.readField(
                pagerTabs, Host.FIELD_PAGER_DEFAULT_SELECTED_TAG);
        String selectedTag = selectedTags.get(0);
        if (!(defaultTag instanceof String) || !allowed.contains((String) defaultTag)) {
            Reflect.writeField(
                    pagerTabs, Host.FIELD_PAGER_DEFAULT_SELECTED_TAG, selectedTag);
        }

        logInfo("F2 home top tabs " + originalTags + " -> " + selectedTags
                + ", selected=" + selectedTag);
    }

    private static List<Integer> selectedIndices(List<?> tags) {
        List<Integer> result = new ArrayList<>(Host.ALLOWED_HOME_TOP_TABS.length);
        for (String allowed : Host.ALLOWED_HOME_TOP_TABS) {
            for (int i = 0; i < tags.size(); i++) {
                if (allowed.equals(tags.get(i))) {
                    result.add(i);
                    break;
                }
            }
        }
        return result;
    }

    private static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    private static List<Object> select(List<?> source, List<Integer> indices, Object fallback) {
        List<Object> result = new ArrayList<>(indices.size());
        for (Integer index : indices) {
            result.add(index >= 0 && index < source.size() ? source.get(index) : fallback);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rewriteTabInfoMap(
            Object pagerTabs, List<String> selectedTags, Set<String> allowed) {
        Object value = Reflect.readField(pagerTabs, Host.FIELD_PAGER_TAB_INFOS);
        if (!(value instanceof Map)) {
            return;
        }
        Map original = (Map) value;
        Map filtered = new HashMap();
        for (String tag : selectedTags) {
            filtered.put(tag, original.get(tag));
        }
        Reflect.writeField(pagerTabs, Host.FIELD_PAGER_TAB_INFOS, filtered);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void retainIconMaps(Object pagerTabs, Set<String> allowed) {
        for (String field : Host.FIELD_PAGER_ICON_MAPS) {
            Object value = Reflect.readField(pagerTabs, field);
            if (!(value instanceof Map)) {
                continue;
            }
            try {
                ((Map) value).keySet().retainAll(allowed);
            } catch (Throwable ignored) {
                // Icon maps are optional metadata; failure must not break the tab list.
            }
        }
    }
}
