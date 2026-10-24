package com.dagu.hyperstorehelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small, failure-tolerant reflection helpers for host classes. */
public final class Reflect {

    private Reflect() {
    }

    public static Class<?> loadClass(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        if (cls == null) {
            return null;
        }
        try {
            return cls.getDeclaredMethod(name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Method findMethodByName(Class<?> cls, String name) {
        if (cls == null) {
            return null;
        }
        for (Method method : cls.getDeclaredMethods()) {
            if (name.equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

    public static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
        for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Keep looking in the superclass.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public static Object readStaticField(Class<?> cls, String name) {
        if (cls == null) {
            return null;
        }
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
                // Keep looking in the superclass.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public static boolean writeField(Object target, String name, Object value) {
        if (target == null) {
            return false;
        }
        for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // Keep looking in the superclass.
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    public static boolean writeStaticField(Class<?> cls, String name, Object value) {
        if (cls == null) {
            return false;
        }
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                field.set(null, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // Keep looking in the superclass.
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    public static Object invokeNoArg(Object target, String methodName) throws Exception {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    public static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return args != null && args.length > 0 && proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "HyperStoreHelperProxy";
            default:
                return null;
        }
    }
}
