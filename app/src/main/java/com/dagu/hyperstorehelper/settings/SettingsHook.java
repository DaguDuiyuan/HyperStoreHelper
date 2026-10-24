package com.dagu.hyperstorehelper.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

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

/** Inject the helper entry and its dedicated settings page into the host settings UI. */
public final class SettingsHook extends BaseHook {

    private static final String ENTRY_KEY = "hyper_store_helper_entry";
    private static final String PAGE_KEY = "hyper_store_helper_page";
    private static final String ENTRY_TITLE = "澎湃商店净化";
    private static final String ENTRY_SUMMARY = "净化开关与重启应用";
    private static final String RESTART_TITLE = "重启应用";
    private static final String RESTART_NOTE_TITLE = "修改后需重启应用商店";
    private static final String RESTART_NOTE_SUMMARY =
            "已打开的页面不会原地重建；重启后五项净化状态会按开关重新应用。";

    private final AtomicBoolean loggedEntry = new AtomicBoolean();
    private final AtomicBoolean loggedPage = new AtomicBoolean();
    private final AtomicBoolean loggedRestart = new AtomicBoolean();

    public SettingsHook(XposedModule module, HelperPrefs prefs) {
        super(module, prefs);
    }

    @Override
    public void install(ClassLoader cl) {
        installPreferenceHooks(cl);
    }

    private void installPreferenceHooks(ClassLoader cl) {
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

        Method onResume = Reflect.findMethod(preferenceFragment, "onResume");
        if (onResume == null) {
            logWarn(Host.CLASS_BASE_MARKET_PREFERENCE + ".onResume() not found");
            return;
        }
        hook(onResume).intercept(chain -> {
            Object result = chain.proceed();
            Object self = chain.getThisObject();
            if (isHelperPage(activityOf(self))) {
                // Host lifecycle bookkeeping expects the original preference keys to
                // exist. Keep them in the hierarchy but hide them again after resume.
                hideHostPreferences(
                        Reflect.invokeNoArgSafe(self, "getPreferenceScreen"),
                        self.getClass().getClassLoader());
            }
            return result;
        });
    }

    private void inject(Object fragment, ClassLoader cl) throws Exception {
        Activity activity = activityOf(fragment);
        if (activity == null) {
            logWarn("settings fragment has no Activity; skipping helper settings");
            return;
        }

        Object screen = Reflect.invokeNoArg(fragment, "getPreferenceScreen");
        if (screen == null) {
            logWarn("settings PreferenceScreen is null; skipping helper settings");
            return;
        }
        Object contextValue = Reflect.invokeNoArg(screen, "getContext");
        if (!(contextValue instanceof Context)) {
            logWarn("settings PreferenceScreen has no Context; skipping helper settings");
            return;
        }
        Context context = (Context) contextValue;
        prefs.attachApplication(context);

        if (Reflect.readField(screen, "mPreferenceManager") == null) {
            Object manager = Reflect.invokeNoArg(fragment, "getPreferenceManager");
            if (manager == null || !Reflect.writeField(screen, "mPreferenceManager", manager)) {
                logWarn("settings PreferenceManager unavailable; skipping helper settings");
                return;
            }
        }

        PreferenceKit kit = new PreferenceKit(context, cl);
        if (isHelperPage(activity)) {
            buildHelperPage(fragment, activity, kit);
        } else {
            addSettingsEntry(screen, activity, kit);
        }
    }

    private void addSettingsEntry(Object screen, Activity activity, PreferenceKit kit)
            throws Exception {
        if (kit.findPreference(screen, ENTRY_KEY) != null) {
            return;
        }
        Object entry = kit.newPreference(kit.preferenceClass);
        kit.setKey.invoke(entry, ENTRY_KEY);
        kit.setTitle.invoke(entry, ENTRY_TITLE);
        kit.setSummary.invoke(entry, ENTRY_SUMMARY);
        kit.setPersistent.invoke(entry, false);
        kit.setOrder.invoke(entry, -10_000);

        Class<?> pageActivity = Class.forName(
                Host.CLASS_MARKET_PREFERENCE_ACTIVITY, false, kit.classLoader);
        Intent pageIntent = new Intent(
                Intent.ACTION_VIEW, Uri.parse("mimarket://marketsettings"));
        pageIntent.setClassName(activity, pageActivity.getName());
        pageIntent.putExtra(Host.EXTRA_HELPER_PAGE, true);
        kit.setIntent.invoke(entry, pageIntent);
        kit.addPreference.invoke(screen, entry);

        if (loggedEntry.compareAndSet(false, true)) {
            logInfo("helper settings entry added");
        }
    }

    private void buildHelperPage(Object fragment, Activity activity, PreferenceKit kit)
            throws Exception {
        Object manager = Reflect.invokeNoArg(fragment, "getPreferenceManager");
        if (manager == null) {
            logWarn("helper page PreferenceManager unavailable");
            return;
        }

        Object page = Reflect.invokeNoArg(fragment, "getPreferenceScreen");
        hideHostPreferences(page, kit.classLoader);
        for (Feature feature : HelperPrefs.FEATURES) {
            kit.addSwitch(page, feature);
        }
        kit.addRestartNote(page);
        activity.setTitle(ENTRY_TITLE);
        installRestartButton(activity, kit.classLoader);

        if (loggedPage.compareAndSet(false, true)) {
            logInfo("dedicated helper settings page built");
        }
    }

    private void hideHostPreferences(Object screen, ClassLoader cl) {
        if (screen == null) {
            return;
        }
        try {
            Class<?> preferenceClass = Class.forName(
                    "androidx.preference.Preference", false, cl);
            Method getPreference = null;
            for (Method method : screen.getClass().getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1
                        && params[0] == int.class
                        && preferenceClass.isAssignableFrom(method.getReturnType())) {
                    getPreference = method;
                    break;
                }
            }
            if (getPreference == null) {
                logWarn("PreferenceGroup.getPreference(int) not found; host items stay visible");
                return;
            }
            Method getKey = preferenceClass.getMethod("getKey");
            Method setVisible = preferenceClass.getMethod("setVisible", boolean.class);
            for (int index = 0; ; index++) {
                Object preference;
                try {
                    preference = getPreference.invoke(screen, index);
                } catch (Throwable endOfGroup) {
                    break;
                }
                if (preference == null) {
                    break;
                }
                Object key = getKey.invoke(preference);
                if (key instanceof String && ((String) key).startsWith("helper_")) {
                    continue;
                }
                setVisible.invoke(preference, false);
            }
        } catch (Throwable throwable) {
            logFailure("hide host settings preferences failed", throwable);
        }
    }

    private void installRestartButton(Activity activity, ClassLoader cl) {
        try {
            Object actionBar = Reflect.readField(activity, Host.FIELD_BASE_ACTION_BAR);
            if (actionBar == null) {
                logWarn("helper action bar unavailable; restart icon skipped");
                return;
            }
            Class<?> actionBarClass = Class.forName(Host.CLASS_ACTION_BAR, false, cl);
            Method setEndView = actionBarClass.getMethod(
                    Host.METHOD_ACTION_BAR_SET_END_VIEW, View.class);
            ImageView button = new ImageView(activity);
            int size = Math.round(48f * activity.getResources().getDisplayMetrics().density);
            button.setLayoutParams(new ViewGroup.LayoutParams(size, size));
            button.setScaleType(ImageView.ScaleType.CENTER);
            int icon = actionBarRefreshIcon(activity);
            if (icon != 0) {
                button.setImageResource(icon);
            }
            boolean night = isNightMode(activity);
            button.setImageTintList(ColorStateList.valueOf(
                    night ? Color.WHITE : Color.BLACK));
            button.setBackground(null);
            button.setContentDescription(RESTART_TITLE);
            button.setClickable(true);
            button.setOnClickListener(view -> restartHost(activity));
            setEndView.invoke(actionBar, button);
            if (loggedRestart.compareAndSet(false, true)) {
                logInfo("helper restart icon added");
            }
        } catch (Throwable throwable) {
            logFailure("add helper restart icon failed", throwable);
        }
    }

    private int actionBarRefreshIcon(Activity activity) {
        boolean night = isNightMode(activity);
        String name = night
                ? "miuix_action_icon_refresh_light"
                : "miuix_action_icon_refresh_dark";
        int icon = activity.getResources().getIdentifier(name, "drawable", Host.PKG);
        if (icon == 0) {
            icon = activity.getResources().getIdentifier(
                    "miuix_appcompat_action_button_refresh_light", "drawable", Host.PKG);
        }
        return icon;
    }

    private static boolean isNightMode(Activity activity) {
        return (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void restartHost(Activity activity) {
        try {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(Host.PKG);
            if (launch == null) {
                logWarn("restart helper failed: launch intent unavailable");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(launch);
            activity.finish();
            logInfo("restart helper launched host main activity");
        } catch (Throwable throwable) {
            logFailure("restart helper failed", throwable);
        }
    }

    private static Activity activityOf(Object fragment) {
        Object value = Reflect.invokeNoArgSafe(fragment, "getActivity");
        return value instanceof Activity ? (Activity) value : null;
    }

    private static boolean isHelperPage(Activity activity) {
        return activity != null
                && activity.getIntent() != null
                && activity.getIntent().getBooleanExtra(Host.EXTRA_HELPER_PAGE, false);
    }

    /** Reflection wrapper for the AndroidX Preference classes already shipped by the host. */
    private final class PreferenceKit {

        final ClassLoader classLoader;
        final Context context;
        final Class<?> screenClass;
        final Class<?> preferenceClass;
        final Class<?> switchClass;
        final Class<?> changeListenerClass;

        final Method setTitle;
        final Method setSummary;
        final Method setKey;
        final Method setOrder;
        final Method setPersistent;
        final Method setSelectable;
        final Method setChecked;
        final Method setIntent;
        final Method setOnPreferenceChangeListener;
        final Method addPreference;
        final Method findPreferenceCharSequence;
        final Method findPreferenceString;

        PreferenceKit(Context context, ClassLoader cl) throws Exception {
            this.classLoader = cl;
            this.context = context;
            this.screenClass = Class.forName(
                    "androidx.preference.PreferenceScreen", false, cl);
            this.preferenceClass = Class.forName(
                    "androidx.preference.Preference", false, cl);
            this.switchClass = Class.forName(
                    "androidx.preference.SwitchPreference", false, cl);

            this.setTitle = preferenceClass.getMethod("setTitle", CharSequence.class);
            this.setSummary = preferenceClass.getMethod("setSummary", CharSequence.class);
            this.setKey = preferenceClass.getMethod("setKey", String.class);
            this.setOrder = preferenceClass.getMethod("setOrder", int.class);
            this.setPersistent = preferenceClass.getMethod("setPersistent", boolean.class);
            this.setSelectable = preferenceClass.getMethod("setSelectable", boolean.class);
            this.setChecked = switchClass.getMethod("setChecked", boolean.class);
            this.setIntent = preferenceClass.getMethod("setIntent", Intent.class);

            Method listenerSetter = null;
            for (Method method : preferenceClass.getMethods()) {
                if ("setOnPreferenceChangeListener".equals(method.getName())
                        && method.getParameterTypes().length == 1) {
                    listenerSetter = method;
                    break;
                }
            }
            if (listenerSetter == null) {
                throw new NoSuchMethodException(
                        "Preference.setOnPreferenceChangeListener");
            }
            // The host obfuscates the listener interface's simple name, so use the
            // exact parameter type exposed by the host's Preference implementation.
            this.changeListenerClass = listenerSetter.getParameterTypes()[0];
            this.setOnPreferenceChangeListener = listenerSetter;
            this.addPreference = findAddPreference(screenClass, preferenceClass);
            this.findPreferenceCharSequence = findPreferenceMethod(
                    screenClass, preferenceClass, CharSequence.class);
            this.findPreferenceString = findPreferenceMethod(
                    screenClass, preferenceClass, String.class);
        }

        private Method findAddPreference(Class<?> screenClass, Class<?> preferenceType) {
            try {
                return screenClass.getMethod("addPreference", preferenceType);
            } catch (Throwable ignored) {
                // Fall through to the obfuscated-method scan.
            }
            Method fallback = null;
            for (Method method : screenClass.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(preferenceType)) {
                    if (method.getReturnType() == Void.TYPE) {
                        return method;
                    }
                    if (fallback == null) {
                        fallback = method;
                    }
                }
            }
            return fallback;
        }

        private Method findPreferenceMethod(
                Class<?> screenClass, Class<?> preferenceType, Class<?> keyType) {
            try {
                return screenClass.getMethod("findPreference", keyType);
            } catch (Throwable ignored) {
                // Fall through to the obfuscated-method scan.
            }
            for (Method method : screenClass.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1
                        && params[0].isAssignableFrom(keyType)
                        && preferenceType.isAssignableFrom(method.getReturnType())) {
                    return method;
                }
            }
            return null;
        }

        Object newPreference(Class<?> cls) throws Exception {
            try {
                Constructor<?> constructor = cls.getConstructor(Context.class);
                return constructor.newInstance(context);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = cls.getConstructor(
                        Context.class, AttributeSet.class);
                return constructor.newInstance(context, null);
            }
        }

        Object findPreference(Object target, String key) throws Exception {
            if (findPreferenceCharSequence != null) {
                return findPreferenceCharSequence.invoke(target, key);
            }
            return findPreferenceString == null
                    ? null : findPreferenceString.invoke(target, key);
        }

        void addSwitch(Object page, Feature feature) throws Exception {
            if (addPreference == null) {
                throw new NoSuchMethodException("PreferenceGroup.addPreference");
            }
            Object preference = newPreference(switchClass);
            setKey.invoke(preference, feature.key);
            setTitle.invoke(preference, feature.title);
            setSummary.invoke(preference, feature.summary);
            setPersistent.invoke(preference, false);
            setChecked.invoke(preference, prefs.isEnabled(feature.key));
            setOnPreferenceChangeListener.invoke(
                    preference, changeListener(feature.key));
            addPreference.invoke(page, preference);
        }

        void addRestartNote(Object page) throws Exception {
            Object preference = newPreference(preferenceClass);
            setKey.invoke(preference, "helper_restart_note");
            setTitle.invoke(preference, RESTART_NOTE_TITLE);
            setSummary.invoke(preference, RESTART_NOTE_SUMMARY);
            setPersistent.invoke(preference, false);
            setSelectable.invoke(preference, false);
            addPreference.invoke(page, preference);
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
