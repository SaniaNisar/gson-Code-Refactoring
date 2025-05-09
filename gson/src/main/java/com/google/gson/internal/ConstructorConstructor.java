/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson.internal;

import com.google.gson.InstanceCreator;
import com.google.gson.JsonIOException;
import com.google.gson.ReflectionAccessFilter;
import com.google.gson.ReflectionAccessFilter.FilterResult;
import com.google.gson.internal.reflect.ReflectionHelper;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public final class ConstructorConstructor {
  private final Map<Type, InstanceCreator<?>> instanceCreators;
  private final boolean useJdkUnsafe;
  private final List<ReflectionAccessFilter> reflectionFilters;

  public ConstructorConstructor(
      Map<Type, InstanceCreator<?>> instanceCreators,
      boolean useJdkUnsafe,
      List<ReflectionAccessFilter> reflectionFilters) {
    this.instanceCreators = instanceCreators;
    this.useJdkUnsafe = useJdkUnsafe;
    this.reflectionFilters = reflectionFilters;
  }

  public <T> ObjectConstructor<T> get(TypeToken<T> typeToken) {
    return get(typeToken, true);
  }

  public <T> ObjectConstructor<T> get(TypeToken<T> typeToken, boolean allowUnsafe) {
    Type type = typeToken.getType();
    Class<? super T> rawType = typeToken.getRawType();

    ObjectConstructor<T> creator = getInstanceCreator(type, rawType);
    if (creator != null) return creator;

    ObjectConstructor<T> special = newSpecialCollectionConstructor(type, rawType);
    if (special != null) return special;

    FilterResult filterResult =
        ReflectionAccessFilterHelper.getFilterResult(reflectionFilters, rawType);
    ObjectConstructor<T> defaultCtor = newDefaultConstructor(rawType, filterResult);
    if (defaultCtor != null) return defaultCtor;

    ObjectConstructor<T> implCtor = newDefaultImplementationConstructor(type, rawType);
    if (implCtor != null) return implCtor;

    String error = checkInstantiable(rawType);
    if (error != null)
      return () -> {
        throw new JsonIOException(error);
      };

    if (!allowUnsafe) {
      return () -> {
        throw new JsonIOException(
            "Unable to create instance of "
                + rawType
                + "; Register an InstanceCreator or a TypeAdapter for this type.");
      };
    }

    if (filterResult != FilterResult.ALLOW) {
      return () -> {
        throw new JsonIOException(
            "Unable to create instance of "
                + rawType
                + "; ReflectionAccessFilter does not permit using reflection or Unsafe. Register an"
                + " InstanceCreator or a TypeAdapter for this type or adjust the access filter to"
                + " allow using reflection.");
      };
    }

    return newUnsafeAllocator(rawType);
  }

  private <T> ObjectConstructor<T> getInstanceCreator(Type type, Class<? super T> rawType) {
    @SuppressWarnings("unchecked")
    InstanceCreator<T> creator = (InstanceCreator<T>) instanceCreators.get(type);
    if (creator != null) return () -> creator.createInstance(type);

    @SuppressWarnings("unchecked")
    InstanceCreator<T> rawCreator = (InstanceCreator<T>) instanceCreators.get(rawType);
    if (rawCreator != null) return () -> rawCreator.createInstance(type);

    return null;
  }

  private <T> ObjectConstructor<T> newUnsafeAllocator(Class<? super T> rawType) {
    if (useJdkUnsafe) {
      return () -> {
        try {
          @SuppressWarnings("unchecked")
          T instance = (T) UnsafeAllocator.INSTANCE.newInstance(rawType);
          return instance;
        } catch (Exception e) {
          throw new RuntimeException(
              "Unable to create instance of "
                  + rawType
                  + ". Registering an InstanceCreator or a TypeAdapter for this type, adding a"
                  + " no-args constructor, or enabling usage of JDK Unsafe may fix this problem.",
              e);
        }
      };
    }

    String msg =
        "Unable to create instance of "
            + rawType
            + "; usage of JDK Unsafe is disabled. Registering an InstanceCreator or a TypeAdapter"
            + " for this type, adding a no-args constructor, or enabling usage of JDK Unsafe may"
            + " fix this problem.";

    if (rawType.getDeclaredConstructors().length == 0) {
      msg += " Or adjust your R8 configuration to retain the no-args constructor.";
    }

    final String finalMsg = msg;
    return () -> {
      throw new JsonIOException(finalMsg);
    };
  }

  private static <T> ObjectConstructor<T> newSpecialCollectionConstructor(
      Type type, Class<? super T> rawType) {

    if (EnumSet.class.isAssignableFrom(rawType)) {
      return () -> {
        if (type instanceof ParameterizedType) {
          Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
          Class<?> raw = GsonTypes.getRawType(elementType);
          if (!Enum.class.isAssignableFrom(raw)) {
            throw new JsonIOException("Invalid EnumSet type: " + type);
          }
          @SuppressWarnings("unchecked")
          Class<? extends Enum> enumClass = (Class<? extends Enum>) raw;
          return (T) EnumSet.noneOf(enumClass);
        } else {
          throw new JsonIOException("Invalid EnumSet type: " + type);
        }
      };
    }

    if (rawType == EnumMap.class) {
      return () -> {
        if (type instanceof ParameterizedType) {
          Type keyType = ((ParameterizedType) type).getActualTypeArguments()[0];
          Class<?> raw = GsonTypes.getRawType(keyType);
          if (!Enum.class.isAssignableFrom(raw)) {
            throw new JsonIOException("Invalid EnumMap type: " + type);
          }
          @SuppressWarnings("unchecked")
          Class<? extends Enum> enumClass = (Class<? extends Enum>) raw;
          return (T) new EnumMap<>(enumClass);
        } else {
          throw new JsonIOException("Invalid EnumMap type: " + type);
        }
      };
    }

    return null;
  }

  private static <T> ObjectConstructor<T> newDefaultConstructor(
      Class<? super T> rawType, FilterResult filterResult) {
    if (Modifier.isAbstract(rawType.getModifiers())) return null;

    Constructor<? super T> constructor;
    try {
      constructor = rawType.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      return null;
    }

    boolean accessible =
        filterResult == FilterResult.ALLOW
            || (ReflectionAccessFilterHelper.canAccess(constructor, null)
                && (filterResult != FilterResult.BLOCK_ALL
                    || Modifier.isPublic(constructor.getModifiers())));

    if (!accessible) {
      return () -> {
        throw new JsonIOException(
            "Unable to invoke no-args constructor of "
                + rawType
                + "; constructor is not accessible and ReflectionAccessFilter does not permit"
                + " making it accessible. Register an InstanceCreator or a TypeAdapter for this"
                + " type, change the visibility of the constructor or adjust the access filter.");
      };
    }

    if (filterResult == FilterResult.ALLOW) {
      String exceptionMsg = ReflectionHelper.tryMakeAccessible(constructor);
      if (exceptionMsg != null) {
        return () -> {
          throw new JsonIOException(exceptionMsg);
        };
      }
    }

    return () -> {
      try {
        @SuppressWarnings("unchecked")
        T instance = (T) constructor.newInstance();
        return instance;
      } catch (InstantiationException | IllegalAccessException e) {
        throw new RuntimeException(
            "Failed to invoke constructor '"
                + ReflectionHelper.constructorToString(constructor)
                + "' with no args",
            e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(
            "Failed to invoke constructor '"
                + ReflectionHelper.constructorToString(constructor)
                + "' with no args",
            e.getCause());
      }
    };
  }

  private static <T> ObjectConstructor<T> newDefaultImplementationConstructor(
      Type type, Class<? super T> rawType) {
    if (Collection.class.isAssignableFrom(rawType)) {
      @SuppressWarnings("unchecked")
      ObjectConstructor<T> ctor = (ObjectConstructor<T>) newCollectionConstructor(rawType);
      return ctor;
    }

    if (Map.class.isAssignableFrom(rawType)) {
      @SuppressWarnings("unchecked")
      ObjectConstructor<T> ctor = (ObjectConstructor<T>) newMapConstructor(type, rawType);
      return ctor;
    }

    return null;
  }

  private static ObjectConstructor<? extends Collection<?>> newCollectionConstructor(
      Class<?> rawType) {
    if (rawType.isAssignableFrom(ArrayList.class)) return ArrayList::new;
    if (rawType.isAssignableFrom(LinkedHashSet.class)) return LinkedHashSet::new;
    if (rawType.isAssignableFrom(TreeSet.class)) return TreeSet::new;
    if (rawType.isAssignableFrom(ArrayDeque.class)) return ArrayDeque::new;
    return null;
  }

  private static ObjectConstructor<? extends Map<?, ?>> newMapConstructor(
      Type type, Class<?> rawType) {
    if (rawType.isAssignableFrom(LinkedTreeMap.class) && hasStringKeyType(type))
      return LinkedTreeMap::new;
    if (rawType.isAssignableFrom(LinkedHashMap.class)) return LinkedHashMap::new;
    if (rawType.isAssignableFrom(TreeMap.class)) return TreeMap::new;
    if (rawType.isAssignableFrom(ConcurrentHashMap.class)) return ConcurrentHashMap::new;
    if (rawType.isAssignableFrom(ConcurrentSkipListMap.class)) return ConcurrentSkipListMap::new;
    return null;
  }

  private static boolean hasStringKeyType(Type mapType) {
    if (!(mapType instanceof ParameterizedType)) return true;
    Type[] typeArgs = ((ParameterizedType) mapType).getActualTypeArguments();
    return typeArgs.length > 0 && GsonTypes.getRawType(typeArgs[0]) == String.class;
  }

  static String checkInstantiable(Class<?> c) {
    int modifiers = c.getModifiers();
    if (Modifier.isInterface(modifiers)) {
      return "Interfaces can't be instantiated! Register an InstanceCreator"
          + " or a TypeAdapter for this type. Interface name: "
          + c.getName();
    }
    if (Modifier.isAbstract(modifiers)) {
      return "Abstract classes can't be instantiated! Adjust the R8 configuration or register"
          + " an InstanceCreator or a TypeAdapter for this type. Class name: "
          + c.getName()
          + "\nSee "
          + TroubleshootingGuide.createUrl("r8-abstract-class");
    }
    return null;
  }

  @Override
  public String toString() {
    return instanceCreators.toString();
  }
}
