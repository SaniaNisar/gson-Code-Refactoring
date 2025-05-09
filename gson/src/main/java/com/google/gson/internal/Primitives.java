/*
 * Copyright (C) 2008 Google Inc.
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

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Contains static utility methods pertaining to primitive types and their corresponding wrapper
 * types.
 *
 * @author Kevin Bourrillion
 */
public final class Primitives {
  private Primitives() {
    /* no instances */
  }

  // Single source of truth for primitive → wrapper
  private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER;
  // Automatically derived inverse mapping wrapper → primitive
  private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE;
  // The set of all wrapper types, for fast lookup
  private static final Set<Class<?>> WRAPPER_TYPES;

  static {
    Map<Class<?>, Class<?>> primToWrap = new HashMap<>(9);
    primToWrap.put(boolean.class, Boolean.class);
    primToWrap.put(byte.class, Byte.class);
    primToWrap.put(char.class, Character.class);
    primToWrap.put(double.class, Double.class);
    primToWrap.put(float.class, Float.class);
    primToWrap.put(int.class, Integer.class);
    primToWrap.put(long.class, Long.class);
    primToWrap.put(short.class, Short.class);
    primToWrap.put(void.class, Void.class);
    PRIMITIVE_TO_WRAPPER = Collections.unmodifiableMap(primToWrap);

    Map<Class<?>, Class<?>> wrapToPrim = new HashMap<>(primToWrap.size());
    for (Map.Entry<Class<?>, Class<?>> e : primToWrap.entrySet()) {
      wrapToPrim.put(e.getValue(), e.getKey());
    }
    WRAPPER_TO_PRIMITIVE = Collections.unmodifiableMap(wrapToPrim);

    WRAPPER_TYPES = Collections.unmodifiableSet(new HashSet<>(wrapToPrim.keySet()));
  }

  /** Returns true if {@code type} is a primitive type (e.g. {@code int.class}). */
  public static boolean isPrimitive(Type type) {
    return (type instanceof Class<?>) && ((Class<?>) type).isPrimitive();
  }

  /**
   * Returns true if {@code type} is one of the nine primitive-wrapper types (Boolean, Byte,
   * Character, Double, Float, Integer, Long, Short, or Void).
   */
  public static boolean isWrapperType(Type type) {
    return (type instanceof Class<?>) && WRAPPER_TYPES.contains(type);
  }

  /**
   * If {@code type} is a primitive (e.g. {@code int.class}), returns its wrapper (e.g. {@link
   * Integer}); otherwise returns {@code type} itself.
   */
  @SuppressWarnings("unchecked")
  public static <T> Class<T> wrap(Class<T> type) {
    Class<?> wrapped = PRIMITIVE_TO_WRAPPER.get(type);
    return (Class<T>) (wrapped != null ? wrapped : type);
  }

  /**
   * If {@code type} is a wrapper (e.g. {@link Integer}), returns its primitive (e.g. {@code
   * int.class}); otherwise returns {@code type} itself.
   */
  @SuppressWarnings("unchecked")
  public static <T> Class<T> unwrap(Class<T> type) {
    Class<?> primitive = WRAPPER_TO_PRIMITIVE.get(type);
    return (Class<T>) (primitive != null ? primitive : type);
  }
}
