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

/** F13: remove sponsored entries from native search result components. */
public final class SearchResultAdsHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();
    private final AtomicBoolean loggedInspectFailure = new AtomicBoolean();

    public SearchResultAdsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> searchFragment = Reflect.loadClass(
                cl, Host.CLASS_NATIVE_SEARCH_RESULT_FRAGMENT);
        Class<?> componentClass = Reflect.loadClass(
                cl, Host.CLASS_BASE_NATIVE_COMPONENT);
        Class<?> appDataBeanClass = Reflect.loadClass(
                cl, Host.CLASS_BASE_APP_DATA_BEAN);
        Method parseResponseData = Reflect.findMethod(
                searchFragment,
                Host.METHOD_SEARCH_PARSE_RESPONSE_DATA,
                JSONObject.class,
                boolean.class);
        Method getComponentType = Reflect.findMethod(
                componentClass, Host.METHOD_COMPONENT_GET_TYPE);
        Method getDataBean = Reflect.findMethod(
                componentClass, Host.METHOD_COMPONENT_GET_DATA_BEAN);
        Method isShowAdTag = Reflect.findMethod(
                appDataBeanClass, Host.METHOD_BASE_APP_IS_SHOW_AD_TAG);

        if (searchFragment == null || parseResponseData == null) {
            logWarn(Host.CLASS_NATIVE_SEARCH_RESULT_FRAGMENT
                    + ".parseResponseData(JSONObject, boolean) not found");
            return;
        }
        if (getComponentType == null) {
            logWarn(Host.CLASS_BASE_NATIVE_COMPONENT
                    + ".getComponentType() not found");
        }
        if (getDataBean == null || isShowAdTag == null) {
            logWarn("F13 search ad-tag detection methods not found");
        }
        if (getComponentType == null && (getDataBean == null || isShowAdTag == null)) {
            logWarn("F13 no usable search ad detection method; skipping");
            return;
        }

        hook(parseResponseData).intercept(chain -> {
            Object result = chain.proceed();
            if (!enabled(HelperPrefs.KEY_SEARCH_RESULT_ADS)
                    || !(result instanceof List)) {
                return result;
            }

            List<?> original = (List<?>) result;
            List<Object> filtered = new ArrayList<>(original.size());
            int removed = 0;
            for (Object component : original) {
                if (component != null
                        && (isExplicitSearchAd(component, getComponentType)
                        || isAdTaggedComponent(
                                component,
                                getDataBean,
                                isShowAdTag,
                                appDataBeanClass))) {
                    removed++;
                    continue;
                }
                filtered.add(component);
            }
            if (removed == 0) {
                return result;
            }
            if (logged.compareAndSet(false, true)) {
                logInfo("F13 search result ads removed count=" + removed);
            }
            return filtered;
        });
    }

    private boolean isExplicitSearchAd(Object component, Method getComponentType) {
        if (getComponentType == null) {
            return false;
        }
        try {
            Object value = getComponentType.invoke(component);
            if (!(value instanceof String)) {
                return false;
            }
            String type = (String) value;
            for (String adType : Host.SEARCH_RESULT_AD_COMPONENT_TYPES) {
                if (adType.equals(type)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable throwable) {
            logInspectFailureOnce("F13 inspect search component type failed", throwable);
            return false;
        }
    }

    private boolean isAdTaggedComponent(
            Object component,
            Method getDataBean,
            Method isShowAdTag,
            Class<?> appDataBeanClass) {
        if (getDataBean == null || isShowAdTag == null || appDataBeanClass == null) {
            return false;
        }
        try {
            Object bean = getDataBean.invoke(component);
            return bean != null
                    && appDataBeanClass.isInstance(bean)
                    && Boolean.TRUE.equals(isShowAdTag.invoke(bean));
        } catch (Throwable throwable) {
            logInspectFailureOnce("F13 inspect search ad tag failed", throwable);
            return false;
        }
    }

    private void logInspectFailureOnce(String message, Throwable throwable) {
        if (loggedInspectFailure.compareAndSet(false, true)) {
            logFailure(message, throwable);
        }
    }
}
