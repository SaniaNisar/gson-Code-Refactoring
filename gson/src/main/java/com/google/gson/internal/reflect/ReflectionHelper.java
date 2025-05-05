package com.google.gson.internal.reflect;

import com.google.gson.JsonIOException;
import com.google.gson.internal.GsonBuildConfig;
import com.google.gson.internal.TroubleshootingGuide;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Utility class for reflection-related operations, including support for Java Records. */
public final class ReflectionHelper {

  private static final RecordHelper RECORD_HELPER;

  static {
    RecordHelper helper;
    try {
      helper = new RecordSupportedHelper();
    } catch (ReflectiveOperationException e) {
      helper = new RecordNotSupportedHelper();
    }
    RECORD_HELPER = helper;
  }

  public static String constructorToString(Constructor<?> constructor) {
    return constructor.getDeclaringClass().getName()
        + getParameterTypesAsString(constructor.getParameterTypes());
  }

  private ReflectionHelper() {
    // Prevent instantiation
  }

  public static void makeAccessible(AccessibleObject object) throws JsonIOException {
    try {
      object.setAccessible(true);
    } catch (Exception e) {
      String description = getAccessibleObjectDescription(object, false);
      throw new JsonIOException(
          "Failed making "
              + description
              + " accessible; either increase its visibility"
              + " or write a custom TypeAdapter for its declaring type."
              + getInaccessibleTroubleshootingSuffix(e),
          e);
    }
  }

  public static String getAccessibleObjectDescription(
      AccessibleObject object, boolean uppercaseFirstLetter) {
    String description;
    if (object instanceof Field) {
      description = "field '" + fieldToString((Field) object) + "'";
    } else if (object instanceof Method) {
      Method method = (Method) object;
      description =
          "method '"
              + method.getDeclaringClass().getName()
              + "#"
              + method.getName()
              + getParameterTypesAsString(method.getParameterTypes())
              + "'";
    } else if (object instanceof Constructor) {
      Constructor<?> constructor = (Constructor<?>) object;
      description =
          "constructor '"
              + constructor.getDeclaringClass().getName()
              + getParameterTypesAsString(constructor.getParameterTypes())
              + "'";
    } else {
      description = "<unknown AccessibleObject> " + object.toString();
    }

    if (uppercaseFirstLetter && !description.isEmpty()) {
      description = Character.toUpperCase(description.charAt(0)) + description.substring(1);
    }

    return description;
  }

  public static String fieldToString(Field field) {
    return field.getDeclaringClass().getName() + "#" + field.getName();
  }

  public static String tryMakeAccessible(Constructor<?> constructor) {
    try {
      constructor.setAccessible(true);
      return null;
    } catch (Exception e) {
      return "Failed making constructor '"
          + constructor.getDeclaringClass().getName()
          + getParameterTypesAsString(constructor.getParameterTypes())
          + "' accessible; either increase its visibility or write a custom InstanceCreator or"
          + " TypeAdapter for its declaring type: "
          + e.getMessage()
          + getInaccessibleTroubleshootingSuffix(e);
    }
  }

  public static boolean isStatic(Class<?> clazz) {
    return Modifier.isStatic(clazz.getModifiers());
  }

  public static boolean isAnonymousOrNonStaticLocal(Class<?> clazz) {
    return !isStatic(clazz) && (clazz.isAnonymousClass() || clazz.isLocalClass());
  }

  public static boolean isRecord(Class<?> clazz) {
    return RECORD_HELPER.isRecord(clazz);
  }

  public static String[] getRecordComponentNames(Class<?> clazz) {
    return RECORD_HELPER.getRecordComponentNames(clazz);
  }

  public static Method getAccessor(Class<?> clazz, Field field) {
    return RECORD_HELPER.getAccessor(clazz, field);
  }

  public static <T> Constructor<T> getCanonicalRecordConstructor(Class<T> clazz) {
    return RECORD_HELPER.getCanonicalRecordConstructor(clazz);
  }

  public static RuntimeException createExceptionForUnexpectedIllegalAccess(
      IllegalAccessException exception) {
    throw new RuntimeException(
        "Unexpected IllegalAccessException occurred (Gson "
            + GsonBuildConfig.VERSION
            + "). Certain ReflectionAccessFilter features require Java >= 9 to work correctly. If"
            + " you are not using ReflectionAccessFilter, report this to the Gson maintainers.",
        exception);
  }

  private static RuntimeException createExceptionForRecordReflectionException(
      ReflectiveOperationException exception) {
    throw new RuntimeException(
        "Unexpected ReflectiveOperationException occurred (Gson "
            + GsonBuildConfig.VERSION
            + "). To support Java records, reflection is utilized to read out information"
            + " about records. All these invocations happen after it is established"
            + " that records exist in the JVM. This exception is unexpected behavior.",
        exception);
  }

  private static String getInaccessibleTroubleshootingSuffix(Exception e) {
    if ("java.lang.reflect.InaccessibleObjectException".equals(e.getClass().getName())) {
      String message = e.getMessage();
      String troubleshootingId =
          (message != null && message.contains("to module com.google.gson"))
              ? "reflection-inaccessible-to-module-gson"
              : "reflection-inaccessible";
      return "\nSee " + TroubleshootingGuide.createUrl(troubleshootingId);
    }
    return "";
  }

  private static String getParameterTypesAsString(Class<?>[] parameterTypes) {
    StringBuilder sb = new StringBuilder("(");
    for (int i = 0; i < parameterTypes.length; i++) {
      sb.append(parameterTypes[i].getSimpleName());
      if (i < parameterTypes.length - 1) {
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  private abstract static class RecordHelper {
    abstract boolean isRecord(Class<?> clazz);

    abstract String[] getRecordComponentNames(Class<?> clazz);

    abstract <T> Constructor<T> getCanonicalRecordConstructor(Class<T> clazz);

    abstract Method getAccessor(Class<?> clazz, Field field);
  }

  private static class RecordSupportedHelper extends RecordHelper {
    private final Method isRecordMethod;
    private final Method getRecordComponentsMethod;
    private final Method getNameMethod;
    private final Method getTypeMethod;

    RecordSupportedHelper() throws NoSuchMethodException, ClassNotFoundException {
      isRecordMethod = Class.class.getMethod("isRecord");
      getRecordComponentsMethod = Class.class.getMethod("getRecordComponents");
      Class<?> recordComponentClass = Class.forName("java.lang.reflect.RecordComponent");
      getNameMethod = recordComponentClass.getMethod("getName");
      getTypeMethod = recordComponentClass.getMethod("getType");
    }

    @Override
    boolean isRecord(Class<?> clazz) {
      try {
        return (boolean) isRecordMethod.invoke(clazz);
      } catch (ReflectiveOperationException e) {
        throw createExceptionForRecordReflectionException(e);
      }
    }

    @Override
    String[] getRecordComponentNames(Class<?> clazz) {
      try {
        Object[] components = (Object[]) getRecordComponentsMethod.invoke(clazz);
        String[] names = new String[components.length];
        for (int i = 0; i < components.length; i++) {
          names[i] = (String) getNameMethod.invoke(components[i]);
        }
        return names;
      } catch (ReflectiveOperationException e) {
        throw createExceptionForRecordReflectionException(e);
      }
    }

    @Override
    <T> Constructor<T> getCanonicalRecordConstructor(Class<T> clazz) {
      try {
        Object[] components = (Object[]) getRecordComponentsMethod.invoke(clazz);
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
          types[i] = (Class<?>) getTypeMethod.invoke(components[i]);
        }
        return clazz.getDeclaredConstructor(types);
      } catch (ReflectiveOperationException e) {
        throw createExceptionForRecordReflectionException(e);
      }
    }

    @Override
    Method getAccessor(Class<?> clazz, Field field) {
      try {
        return clazz.getMethod(field.getName());
      } catch (ReflectiveOperationException e) {
        throw createExceptionForRecordReflectionException(e);
      }
    }
  }

  private static class RecordNotSupportedHelper extends RecordHelper {

    @Override
    boolean isRecord(Class<?> clazz) {
      return false;
    }

    @Override
    String[] getRecordComponentNames(Class<?> clazz) {
      throw new UnsupportedOperationException("Records are not supported on this JVM.");
    }

    @Override
    <T> Constructor<T> getCanonicalRecordConstructor(Class<T> clazz) {
      throw new UnsupportedOperationException("Records are not supported on this JVM.");
    }

    @Override
    Method getAccessor(Class<?> clazz, Field field) {
      throw new UnsupportedOperationException("Records are not supported on this JVM.");
    }
  }
}
