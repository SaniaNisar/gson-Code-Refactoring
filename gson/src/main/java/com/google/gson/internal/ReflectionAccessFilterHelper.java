/*
 * Copyright (C) 2022 Google Inc.
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

import com.google.gson.ReflectionAccessFilter;
import com.google.gson.ReflectionAccessFilter.FilterResult;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;

/** Internal helper class for {@link ReflectionAccessFilter}. */
public final class ReflectionAccessFilterHelper {
  private ReflectionAccessFilterHelper() {}

  // Platform type detection is based on Moshi's Util.isPlatformType(Class)
  // See https://github.com/square/moshi/.../Util.java#L141

  public static boolean isJavaType(Class<?> c) {
    return isJavaType(c.getName());
  }

  private static boolean isJavaType(String className) {
    return className.startsWith("java.") || className.startsWith("javax.");
  }

  public static boolean isAndroidType(Class<?> c) {
    return isAndroidType(c.getName());
  }

  private static boolean isAndroidType(String className) {
    return className.startsWith("android.")
        || className.startsWith("androidx.")
        || isJavaType(className);
  }

  public static boolean isAnyPlatformType(Class<?> c) {
    String className = c.getName();
    return isAndroidType(className) // Covers Android and Java
        || className.startsWith("kotlin.")
        || className.startsWith("kotlinx.")
        || className.startsWith("scala.");
  }

  /**
   * Gets the result of applying all filters until the first one returns a result other than {@link
   * FilterResult#INDECISIVE}, or {@link FilterResult#ALLOW} if none did.
   */
  public static FilterResult getFilterResult(
      List<ReflectionAccessFilter> reflectionFilters, Class<?> c) {
    if (reflectionFilters == null || reflectionFilters.isEmpty()) {
      return FilterResult.ALLOW;
    }
    for (ReflectionAccessFilter filter : reflectionFilters) {
      FilterResult result = filter.check(c);
      if (result != FilterResult.INDECISIVE) {
        return result;
      }
    }
    return FilterResult.ALLOW;
  }

  /** See {@link AccessibleObject#canAccess(Object)} (Java 9+), or assume “true” on Java 8. */
  public static boolean canAccess(AccessibleObject obj, Object instance) {
    return CAN_ACCESS.invoke(obj, instance);
  }

  // Reflectively look up AccessibleObject.canAccess once
  private static final Method CAN_ACCESS_METHOD = findCanAccess();
  private static final AccessInvoker CAN_ACCESS =
      CAN_ACCESS_METHOD != null
          ? (obj, instance) -> {
            try {
              return (Boolean) CAN_ACCESS_METHOD.invoke(obj, instance);
            } catch (Exception e) {
              throw new RuntimeException("Failed invoking canAccess", e);
            }
          }
          : (obj, instance) -> true;

  @FunctionalInterface
  private interface AccessInvoker {
    boolean invoke(AccessibleObject obj, Object instance);
  }

  private static Method findCanAccess() {
    if (!JavaVersion.isJava9OrLater()) {
      return null;
    }
    try {
      Method m = AccessibleObject.class.getDeclaredMethod("canAccess", Object.class);
      m.setAccessible(true);
      return m;
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }
}
