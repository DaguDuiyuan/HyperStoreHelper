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

/** F9: remove recommendation blocks from the download queue page. */
public final class DownloadRecommendationsHook extends BaseHook {

    private final AtomicBoolean loggedGroups = new AtomicBoolean();
    private final AtomicBoolean loggedUnActive = new AtomicBoolean();

    public DownloadRecommendationsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> downloadFragment = Reflect.loadClass(cl, Host.CLASS_DOWNLOAD_LIST_FRAGMENT);
        Class<?> recommendGroup = Reflect.loadClass(cl, Host.CLASS_RECOMMEND_GROUP_INFO);
        Method setRecommendGroup = Reflect.findMethod(
                downloadFragment, Host.METHOD_DOWNLOAD_SET_RECOMMEND_GROUP, List.class);
        Method addUnActiveApp = Reflect.findMethod(
                downloadFragment, Host.METHOD_DOWNLOAD_ADD_UNACTIVE, String.class);

        if (setRecommendGroup == null) {
            logWarn(Host.CLASS_DOWNLOAD_LIST_FRAGMENT + ".setRecommendGroup(List) not found");
        } else {
            hook(setRecommendGroup).intercept(chain -> {
                if (!enabled(HelperPrefs.KEY_DOWNLOAD_RECOMMENDATIONS)
                        || !(chain.getArg(0) instanceof List)) {
                    return chain.proceed();
                }
                List<Object> filtered = filterGroups(
                        (List<?>) chain.getArg(0), recommendGroup);
                return filtered == null
                        ? chain.proceed()
                        : chain.proceed(new Object[]{filtered});
            });
        }

        if (addUnActiveApp == null) {
            logWarn(Host.CLASS_DOWNLOAD_LIST_FRAGMENT + ".addUnActiveApp(String) not found");
            return;
        }
        hook(addUnActiveApp).intercept(chain -> {
            if (enabled(HelperPrefs.KEY_DOWNLOAD_RECOMMENDATIONS)) {
                if (loggedUnActive.compareAndSet(false, true)) {
                    logInfo("F9 recent install section suppressed");
                }
                return null;
            }
            return chain.proceed();
        });
    }

    private List<Object> filterGroups(List<?> original, Class<?> groupClass) {
        List<Object> filtered = new ArrayList<>(original.size());
        int removed = 0;
        for (Object group : original) {
            if (group == null || !groupClass.isInstance(group)) {
                filtered.add(group);
                continue;
            }
            Object position = Reflect.readField(
                    group, Host.FIELD_RECOMMEND_GROUP_REC_POSITION);
            Object title = Reflect.readField(group, Host.FIELD_RECOMMEND_GROUP_TITLE);
            String positionText = position instanceof String ? (String) position : "";
            String titleText = title instanceof String ? (String) title : "";
            boolean remove = Host.REC_POSITION_INSTALL_RECORD.equals(positionText)
                    || Host.REC_POSITION_REC_FOR_YOU.equals(positionText);
            if (!remove && !Host.REC_POSITION_DOWNLOAD_QUEUE.equals(positionText)) {
                for (String target : Host.DOWNLOAD_RECOMMENDATION_TITLES) {
                    if (target.equals(titleText)) {
                        remove = true;
                        break;
                    }
                }
            }
            if (remove) {
                removed++;
            } else {
                filtered.add(group);
            }
        }
        if (removed == 0) {
            return null;
        }
        if (loggedGroups.compareAndSet(false, true)) {
            logInfo("F9 download recommendation groups removed count=" + removed);
        }
        return filtered;
    }
}
