package com.dagu.hyperstorehelper.hooks;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F11: remove recommendation blocks from the app update page. */
public final class UpdateRecommendationsHook extends BaseHook {

    private final AtomicBoolean loggedV2 = new AtomicBoolean();
    private final AtomicBoolean loggedV1 = new AtomicBoolean();

    public UpdateRecommendationsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        installV2(cl);
        installV1(cl);
    }

    private void installV2(ClassLoader cl) {
        Class<?> fragment = Reflect.loadClass(cl, Host.CLASS_UPDATE_LIST_V2_FRAGMENT);
        Method onRefreshPageData = Reflect.findMethod(
                fragment,
                Host.METHOD_UPDATE_ON_REFRESH_PAGE_DATA,
                List.class,
                boolean.class,
                JSONObject.class);
        if (onRefreshPageData == null) {
            onRefreshPageData = Reflect.findMethodByName(
                    fragment, Host.METHOD_UPDATE_ON_REFRESH_PAGE_DATA);
        }
        if (onRefreshPageData == null) {
            logWarn(Host.CLASS_UPDATE_LIST_V2_FRAGMENT
                    + ".onRefreshPageData(List, boolean, JSONObject) not found");
            return;
        }
        hook(onRefreshPageData).intercept(chain -> {
            if (!enabled(HelperPrefs.KEY_UPDATE_PAGE_RECOMMENDATIONS)) {
                return chain.proceed();
            }
            if (loggedV2.compareAndSet(false, true)) {
                logInfo("F11 update-page recommendation components suppressed");
            }
            return chain.proceed(new Object[]{
                    new ArrayList<>(),
                    chain.getArg(1),
                    chain.getArg(2),
            });
        });
    }

    /**
     * The 4.129.s.00 update page uses UpdateListFragment. Suppress only the
     * rendered recommendation items so the host still completes its loading
     * and update-list rendering flow.
     */
    private void installV1(ClassLoader cl) {
        Class<?> fragment = Reflect.loadClass(cl, Host.CLASS_UPDATE_LIST_FRAGMENT);
        Class<?> adapter = Reflect.loadClass(cl, Host.CLASS_UPDATE_LIST_RV_ADAPTER);

        Method generateRecommendGroupItems = Reflect.findMethod(
                adapter,
                Host.METHOD_UPDATE_GENERATE_RECOMMEND_GROUP_ITEMS,
                ArrayList.class,
                int.class);
        if (generateRecommendGroupItems == null) {
            logWarn(Host.CLASS_UPDATE_LIST_RV_ADAPTER
                    + ".generateRecommendGroupItems(ArrayList, int) not found");
        } else {
            hook(generateRecommendGroupItems).intercept(chain -> {
                if (!enabled(HelperPrefs.KEY_UPDATE_PAGE_RECOMMENDATIONS)) {
                    return chain.proceed();
                }
                if (loggedV1.compareAndSet(false, true)) {
                    logInfo("F11 update-page recommendation groups suppressed");
                }
                return null;
            });
        }

        Method addRelatedRecommend = Reflect.findMethod(
                adapter,
                Host.METHOD_UPDATE_ADD_RELATED_RECOMMEND,
                String.class,
                List.class);
        if (addRelatedRecommend == null) {
            logWarn(Host.CLASS_UPDATE_LIST_RV_ADAPTER
                    + ".addRelatedRecommend(String, List) not found");
        } else {
            hook(addRelatedRecommend).intercept(chain -> {
                if (enabled(HelperPrefs.KEY_UPDATE_PAGE_RECOMMENDATIONS)) {
                    if (loggedV1.compareAndSet(false, true)) {
                        logInfo("F11 update-page related apps suppressed");
                    }
                    return null;
                }
                return chain.proceed();
            });
        }

        Method updateGridRecommend = Reflect.findMethod(
                fragment, Host.METHOD_UPDATE_GRID_RECOMMEND);
        if (updateGridRecommend == null) {
            logWarn(Host.CLASS_UPDATE_LIST_FRAGMENT + ".updateGridRecommend() not found");
        } else {
            hook(updateGridRecommend).intercept(chain -> {
                if (enabled(HelperPrefs.KEY_UPDATE_PAGE_RECOMMENDATIONS)) {
                    if (loggedV1.compareAndSet(false, true)) {
                        logInfo("F11 update-page recommendation grid suppressed");
                    }
                    return null;
                }
                return chain.proceed();
            });
        }
    }
}
