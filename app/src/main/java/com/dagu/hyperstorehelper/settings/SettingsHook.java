package com.dagu.hyperstorehelper.settings;

import android.content.Context;
import android.os.Bundle;

import com.dagu.hyperstorehelper.Host;
import com.dagu.hyperstorehelper.Reflect;
import com.dagu.hyperstorehelper.core.BaseHook;
import com.dagu.hyperstorehelper.core.Feature;
import com.dagu.hyperstorehelper.core.HelperPrefs;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Inject seven independent switches into the host's "My -> Settings" page. */
public final class SettingsHook extends BaseHook {

    private static final String CATEGORY_KEY = "hyper_store_helper_category";
    private static final String CATEGORY_TITLE = "澎湃商店净化";
    private static final String RESTART_TITLE = "修改后需重启应用商店";
    private static final String RESTART_SUMMARY =
            "已打开的页面不会原地重建；重启后七项净化状态会按开关重新应用。";

    private final AtomicBoolean logged = new AtomicBoolean();

    public SettingsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> preferenceFragment = Reflect.loadClass(
                cl, Host.CLASS_BASE_MARKET_PREFERENCE);
        Method onCreatePreferences = Reflect.findMethod(
                preferenceFragment,
                Host.METHOD_ON_CREATE_PREFERENCES,
                Bundle.class,
                String.class);
        if (onCreatePreferences == null) {
            logWarn(Host.CLASS_BASE_MARKET_PREFERENCE
                    + ".onCreatePreferences(Bundle, String) not found");
            return;
        }
        hook(onCreatePreferences).intercept(chain -> {
            Object result = chain.proceed();
            try {
                inject(chain.getThisObject(), cl);
            } catch (Throwable throwable) {
                logFailure("inject helper settings failed", throwable);
            }
            return result;
        });
    }

    private void inject(Object fragment, ClassLoader cl) throws Exception {
        Object screen = Reflect.invokeNoArg(fragment, "getPreferenceScreen");
        if (screen == null) {
            logWarn("settings PreferenceScreen is null; skipping helper category");
            return;
        }
        Object contextValue = Reflect.invokeNoArg(screen, "getContext");
        if (!(contextValue instanceof Context)) {
            logWarn("settings PreferenceScreen has no Context; skipping helper category");
            return;
        }
        Context context = (Context) contextValue;
        prefs.attachApplication(context);

        PreferenceKit kit = new PreferenceKit(screen, context, cl);
        if (kit.findPreference(screen, CATEGORY_KEY) != null) {
            return;
        }

        Object category = kit.newPreference(kit.categoryClass);
        kit.setTitle.invoke(category, CATEGORY_TITLE);
        kit.setKey.invoke(category, CATEGORY_KEY);
        kit.setPersistent.invoke(category, false);
        kit.setOrder.invoke(category, -10_000);

        for (Feature feature : HelperPrefs.FEATURES) {
            kit.addSwitch(category, feature);
        }
        kit.addRestartNote(category);
        kit.addPreference.invoke(kit.screen, category);

        if (logged.compareAndSet(false, true)) {
            logInfo("helper settings category added");
        }
    }

    /** Reflection wrapper for the AndroidX Preference classes already shipped by the host. */
    private final class PreferenceKit {

        final Object screen;
        final Context context;
        final Class<?> preferenceClass;
        final Class<?> categoryClass;
        final Class<?> switchClass;
        final Class<?> changeListenerClass;

        final Method setTitle;
        final Method setSummary;
        final Method setKey;
        final Method setOrder;
        final Method setPersistent;
        final Method setSelectable;
        final Method setChecked;
        final Method setOnPreferenceChangeListener;
        final Method addPreference;
        final Method findPreferenceCharSequence;
        final Method findPreferenceString;

        PreferenceKit(Object screen, Context context, ClassLoader cl) throws Exception {
            this.screen = screen;
            this.context = context;
            this.preferenceClass = Class.forName(
                    "androidx.preference.Preference", false, cl);
            this.categoryClass = Class.forName(
                    "androidx.preference.PreferenceCategory", false, cl);
            this.switchClass = Class.forName(
                    "androidx.preference.SwitchPreference", false, cl);
            this.changeListenerClass = Class.forName(
                    "androidx.preference.Preference$OnPreferenceChangeListener", false, cl);

            this.setTitle = preferenceClass.getMethod("setTitle", CharSequence.class);
            this.setSummary = preferenceClass.getMethod("setSummary", CharSequence.class);
            this.setKey = preferenceClass.getMethod("setKey", String.class);
            this.setOrder = preferenceClass.getMethod("setOrder", int.class);
            this.setPersistent = preferenceClass.getMethod("setPersistent", boolean.class);
            this.setSelectable = preferenceClass.getMethod("setSelectable", boolean.class);
            this.setChecked = switchClass.getMethod("setChecked", boolean.class);
            this.setOnPreferenceChangeListener = preferenceClass.getMethod(
                    "setOnPreferenceChangeListener", changeListenerClass);
            this.addPreference = screen.getClass().getMethod(
                    "addPreference", preferenceClass);

            Method charSequence;
            Method string;
            try {
                charSequence = screen.getClass().getMethod(
                        "findPreference", CharSequence.class);
            } catch (Throwable ignored) {
                charSequence = null;
            }
            try {
                string = screen.getClass().getMethod("findPreference", String.class);
            } catch (Throwable ignored) {
                string = null;
            }
            this.findPreferenceCharSequence = charSequence;
            this.findPreferenceString = string;
        }

        Object newPreference(Class<?> cls) throws Exception {
            Constructor<?> constructor = cls.getConstructor(Context.class);
            return constructor.newInstance(context);
        }

        Object findPreference(Object target, String key) throws Exception {
            if (findPreferenceCharSequence != null) {
                return findPreferenceCharSequence.invoke(target, key);
            }
            return findPreferenceString == null
                    ? null : findPreferenceString.invoke(target, key);
        }

        void addSwitch(Object category, Feature feature) throws Exception {
            Object preference = newPreference(switchClass);
            setKey.invoke(preference, feature.key);
            setTitle.invoke(preference, feature.title);
            setSummary.invoke(preference, feature.summary);
            setPersistent.invoke(preference, false);
            setChecked.invoke(preference, prefs.isEnabled(feature.key));
            setOnPreferenceChangeListener.invoke(
                    preference, changeListener(feature.key));
            addPreference.invoke(category, preference);
        }

        void addRestartNote(Object category) throws Exception {
            Object preference = newPreference(preferenceClass);
            setTitle.invoke(preference, RESTART_TITLE);
            setSummary.invoke(preference, RESTART_SUMMARY);
            setPersistent.invoke(preference, false);
            setSelectable.invoke(preference, false);
            addPreference.invoke(category, preference);
        }

        Object changeListener(String key) {
            return Proxy.newProxyInstance(
                    changeListenerClass.getClassLoader(),
                    new Class<?>[]{changeListenerClass},
                    (proxy, method, args) -> {
                        if ("onPreferenceChange".equals(method.getName())) {
                            Object value = args != null && args.length > 1 ? args[1] : null;
                            if (value instanceof Boolean) {
                                prefs.write(key, (Boolean) value);
                            }
                            return Boolean.TRUE;
                        }
                        return Reflect.objectMethod(proxy, method, args);
                    });
        }
    }
}
