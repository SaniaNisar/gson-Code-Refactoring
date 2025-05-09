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

import java.util.Objects;

/**
 * Utility class for enforcing method preconditions. Use standard JDK alternatives like {@link
 * Objects#requireNonNull(Object)} where possible.
 */
public final class GsonPreconditions {
  private GsonPreconditions() {
    throw new UnsupportedOperationException("No instances.");
  }

  /**
   * @deprecated Use {@link Objects#requireNonNull(Object)} instead.
   */
  @Deprecated
  public static <T> T checkNotNull(T obj) {
    return checkNotNull(obj, "Expected non-null reference");
  }

  /**
   * @deprecated Use {@link Objects#requireNonNull(Object, String)} instead.
   */
  @Deprecated
  public static <T> T checkNotNull(T obj, String message) {
    if (obj == null) {
      throw new NullPointerException(message);
    }
    return obj;
  }

  public static void checkArgument(boolean condition) {
    if (!condition) {
      throw new IllegalArgumentException("Condition failed");
    }
  }

  public static void checkArgument(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
