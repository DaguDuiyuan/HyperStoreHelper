package com.dagu.hyperstorehelper.hooks;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** F8: remove ad-tagged entries from the game ranking and renumber remaining items. */
public final class GameRankAdsHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();

    public GameRankAdsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> rankFragment = Reflect.loadClass(cl, Host.CLASS_RANK_TAB_FRAGMENT);
        Class<?> componentClass = Reflect.loadClass(cl, Host.CLASS_BASE_NATIVE_COMPONENT);
        Class<?> appDataBeanClass = Reflect.loadClass(cl, Host.CLASS_BASE_APP_DATA_BEAN);
        Method getDataBean = Reflect.findMethod(
                componentClass, Host.METHOD_COMPONENT_GET_DATA_BEAN);
        Method isShowAdTag = Reflect.findMethod(
                appDataBeanClass, Host.METHOD_BASE_APP_IS_SHOW_AD_TAG);
        Method tryAddRank = Reflect.findMethod(
                rankFragment,
                Host.METHOD_RANK_TRY_ADD_RANK,
                int.class,
                List.class,
                boolean.class);
        Method tryFilter = Reflect.findMethod(
                rankFragment, Host.METHOD_RANK_TRY_FILTER, List.class);

        if (getDataBean == null || isShowAdTag == null) {
            logWarn("F8 rank ad detection methods not found");
            return;
        }
        if (tryAddRank == null) {
            logWarn(Host.CLASS_RANK_TAB_FRAGMENT + ".tryAddRank(int, List, boolean) not found");
        } else {
            hook(tryAddRank).intercept(chain -> {
                Object fragment = chain.getThisObject();
                if (!enabled(HelperPrefs.KEY_GAME_RANK_ADS) || !isGameRank(fragment)) {
                    return chain.proceed();
                }
                List<Object> filtered = filterAds(
                        chain.getArg(1), getDataBean, isShowAdTag, appDataBeanClass);
                if (filtered == null) {
                    return chain.proceed();
                }
                return chain.proceed(new Object[]{
                        chain.getArg(0), filtered, chain.getArg(2)});
            });
        }

        if (tryFilter == null) {
            logWarn(Host.CLASS_RANK_TAB_FRAGMENT + ".tryFilter(List) not found");
            return;
        }
        hook(tryFilter).intercept(chain -> {
            Object fragment = chain.getThisObject();
            if (!enabled(HelperPrefs.KEY_GAME_RANK_ADS) || !isGameRank(fragment)) {
                return chain.proceed();
            }
            List<Object> filtered = filterAds(
                    chain.getArg(0), getDataBean, isShowAdTag, appDataBeanClass);
            return filtered == null
                    ? chain.proceed()
                    : chain.proceed(new Object[]{filtered});
        });
    }

    @SuppressWarnings("unchecked")
    private List<Object> filterAds(
            Object value,
            Method getDataBean,
            Method isShowAdTag,
            Class<?> appDataBeanClass) {
        if (!(value instanceof List)) {
            return null;
        }
        List<?> original = (List<?>) value;
        List<Object> filtered = new ArrayList<>(original.size());
        int removed = 0;
        for (Object component : original) {
            try {
                Object bean = component == null ? null : getDataBean.invoke(component);
                if (bean != null
                        && appDataBeanClass.isInstance(bean)
                        && Boolean.TRUE.equals(isShowAdTag.invoke(bean))) {
                    removed++;
                    continue;
                }
            } catch (Throwable throwable) {
                logFailure("F8 inspect rank ad failed", throwable);
            }
            filtered.add(component);
        }
        if (removed == 0) {
            return null;
        }
        if (logged.compareAndSet(false, true)) {
            logInfo("F8 game rank ads removed count=" + removed);
        }
        return filtered;
    }

    private static boolean isGameRank(Object fragment) {
        Object tagValue = Reflect.invokeNoArgSafe(
                fragment, Host.METHOD_RANK_GET_PAGE_TAB_TAG);
        if (!(tagValue instanceof String)) {
            return false;
        }
        return ((String) tagValue).toLowerCase(Locale.ROOT)
                .contains(Host.RANK_GAME_TAB_MARKER);
    }
}
