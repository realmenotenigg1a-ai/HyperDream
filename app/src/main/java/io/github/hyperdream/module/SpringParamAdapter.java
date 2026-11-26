package io.github.hyperdream.module;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Strict structural adapter; never mutates launcher-owned parameter objects or arrays. */
final class SpringParamAdapter {
    private final Class<?> parameterType;
    private final Constructor<?> constructor;
    private final Field key;
    private final Field damping;
    private final Field response;
    private final Field impulse;

    SpringParamAdapter(Class<?> parameterType) throws ReflectiveOperationException {
        this.parameterType = parameterType;
        constructor = parameterType.getDeclaredConstructor(
                String.class, float.class, float.class, float.class);
        constructor.setAccessible(true);
        key = field(parameterType, "key", String.class);
        damping = field(parameterType, "damping", float.class);
        response = field(parameterType, "response", float.class);
        impulse = field(parameterType, "impulse", float.class);
    }

    private static Field field(Class<?> type, String name, Class<?> expected)
            throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        if (field.getType() != expected || Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException("Unexpected spring field: " + name);
        }
        field.setAccessible(true);
        return field;
    }

    Object copyWithOverrides(Object original, SpringSettings settings)
            throws ReflectiveOperationException {
        if (!settings.enabled) {
            return original;
        }
        if (original == null) { return null; }
        if (!original.getClass().isArray()
                || original.getClass().getComponentType() != parameterType) {
            throw new IllegalArgumentException("Unexpected spring bundle type");
        }
        int length = Array.getLength(original);
        if (length == 0) { return original; }
        if (length > 64) {
            throw new IllegalArgumentException("Unexpected spring bundle size");
        }
        Object replacement = Array.newInstance(parameterType, length);
        for (int i = 0; i < length; i++) {
            Object item = Array.get(original, i);
            if (item == null) {
                throw new IllegalArgumentException("Missing spring parameter");
            }
            float oldDamping = damping.getFloat(item);
            float oldImpulse = impulse.getFloat(item);
            Object name = key.get(item);
            if (!(name instanceof String) || !Float.isFinite(oldDamping)
                    || oldDamping <= 0f || !Float.isFinite(oldImpulse)) {
                throw new IllegalArgumentException("Unexpected spring parameter values");
            }
            float newResponse = settings.adjustedResponse(response.getFloat(item));
            Array.set(replacement, i, constructor.newInstance(name, settings.damping,
                    newResponse, oldImpulse));
        }
        // Return only after the entire copy succeeds. A failure leaves the original intact.
        return replacement;
    }

    static boolean supportsAnimation(String name) {
        return "OPEN_FROM_HOME".equals(name) || "CLOSE_TO_HOME".equals(name);
    }
}
