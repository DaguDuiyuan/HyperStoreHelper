package com.dagu.hyperstorehelper.hooks;

import android.net.Uri;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * Enable the host's existing H5 proxy LRU cache for the Golden Mi Award data
 * request. The cache key is bucketed by day so the page still refreshes.
 */
public final class GoldenAwardCacheHook extends BaseHook {

    private final AtomicBoolean logged = new AtomicBoolean();
    private final AtomicBoolean loggedCacheStatus = new AtomicBoolean();

    public GoldenAwardCacheHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> webEvent = Reflect.loadClass(cl, Host.CLASS_WEB_EVENT);
        Method request = Reflect.findMethod(
                webEvent, Host.METHOD_WEB_EVENT_REQUEST, String.class);
        if (request == null) {
            logWarn(Host.CLASS_WEB_EVENT + ".request(String) not found");
            return;
        }
        hook(request).intercept(chain -> {
            Object argument = chain.getArg(0);
            if (!(argument instanceof String)) {
                return chain.proceed();
            }
            String cachedRequest = enableGoldenAwardCache((String) argument);
            if (cachedRequest == null) {
                return chain.proceed();
            }
            if (logged.compareAndSet(false, true)) {
                logInfo("Golden Mi Award H5 request cache enabled");
            }
            return chain.proceed(new Object[]{cachedRequest});
        });

        Method sendCallback = Reflect.findMethod(
                webEvent,
                Host.METHOD_WEB_EVENT_SEND_CALLBACK,
                String.class,
                String.class);
        if (sendCallback == null) {
            logWarn(Host.CLASS_WEB_EVENT + ".sendDataToCallback(String, String) not found");
            return;
        }
        hook(sendCallback).intercept(chain -> {
            Object payload = chain.getArg(1);
            if (payload instanceof String
                    && ((String) payload).contains("\"requestIsCache\"")
                    && loggedCacheStatus.compareAndSet(false, true)) {
                boolean hit = ((String) payload).contains("\"requestIsCache\":true");
                logInfo("Golden Mi Award H5 cache " + (hit ? "hit" : "miss"));
            }
            return chain.proceed();
        });
    }

    private String enableGoldenAwardCache(String rawRequest) {
        try {
            JSONObject request = new JSONObject(rawRequest);
            String url = request.optString("url", "");
            if (!isGoldenAwardDataRequest(url)) {
                return null;
            }
            JSONObject params = request.optJSONObject("params");
            if (params == null) {
                params = new JSONObject();
                request.put("params", params);
            }
            params.put(Host.WEB_EVENT_PARAM_SHOULD_INTERCEPT, "true");
            params.put(Host.WEB_EVENT_PARAM_NEED_LRU_CACHE, "true");
            params.put(Host.WEB_EVENT_PARAM_PROXY_TIMEOUT, "1");
            params.put(Host.WEB_EVENT_PARAM_DIGEST, buildDigest(url, params).toString());
            return request.toString();
        } catch (Throwable throwable) {
            logFailure("enable Golden Mi Award H5 cache failed", throwable);
            return null;
        }
    }

    private static boolean isGoldenAwardDataRequest(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try {
            String path = Uri.parse(url).getPath();
            return Host.GOLDEN_AWARD_REQUEST_PATH.equals(path);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * The host LRU implementation keys entries only by digestParams. The digest
     * therefore has to describe the complete logical request, otherwise every
     * page or Gold Mi request in the same day would share one response.
     */
    private static JSONObject buildDigest(String url, JSONObject params)
            throws JSONException {
        TreeMap<String, String> sorted = new TreeMap<>();
        Iterator<String> keys = params.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (isCacheControlParam(key)) {
                continue;
            }
            sorted.put(key, params.optString(key, ""));
        }

        StringBuilder identity = new StringBuilder(url.length() + 128);
        identity.append("v2\n").append(url);
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            identity.append('\n')
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue());
        }

        JSONObject digest = new JSONObject();
        digest.put("day", new SimpleDateFormat("yyyyMMdd", Locale.ROOT)
                .format(new Date()));
        digest.put("request", identity.toString());
        return digest;
    }

    private static boolean isCacheControlParam(String key) {
        return Host.WEB_EVENT_PARAM_SHOULD_INTERCEPT.equals(key)
                || Host.WEB_EVENT_PARAM_NEED_LRU_CACHE.equals(key)
                || Host.WEB_EVENT_PARAM_PROXY_TIMEOUT.equals(key)
                || Host.WEB_EVENT_PARAM_DIGEST.equals(key);
    }
}
