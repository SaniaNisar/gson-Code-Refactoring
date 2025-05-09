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

import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Do sneaky things to allocate objects without invoking their constructors.
 *
 * @author Joel Leitch
 * @author Jesse Wilson
 */
public abstract class UnsafeAllocator {
  public abstract <T> T newInstance(Class<T> c) throws Exception;

  /** Safeguard against trying to instantiate interfaces or abstracts. */
  private static void assertInstantiable(Class<?> c) {
    String msg = ConstructorConstructor.checkInstantiable(c);
    if (msg != null) {
      throw new AssertionError("UnsafeAllocator is used for non-instantiable type: " + msg);
    }
  }

  public static final UnsafeAllocator INSTANCE = create();

  private UnsafeAllocator() {
    // no subclasses
  }

  /** Try several back‐doors (sun.misc.Unsafe, ObjectStreamClass, ObjectInputStream) in turn. */
  @SuppressWarnings("EmptyCatch") // we intentionally swallow ALL Throwables here
  private static UnsafeAllocator create() {
    // 1) sun.misc.Unsafe#allocateInstance
    try {
      Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      Field f = unsafeClass.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      Object unsafe = f.get(null);
      Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
      return new UnsafeAllocator() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T newInstance(Class<T> c) throws Exception {
          assertInstantiable(c);
          return (T) allocateInstance.invoke(unsafe, c);
        }
      };
    } catch (Throwable ignored) {
    }

    // 2) ObjectStreamClass.newInstance (Android post‐Gingerbread)
    try {
      Method getCtorId = ObjectStreamClass.class.getDeclaredMethod("getConstructorId", Class.class);
      getCtorId.setAccessible(true);
      int ctorId = (Integer) getCtorId.invoke(null, Object.class);

      Method newInstance =
          ObjectStreamClass.class.getDeclaredMethod("newInstance", Class.class, int.class);
      newInstance.setAccessible(true);

      return new UnsafeAllocator() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T newInstance(Class<T> c) throws Exception {
          assertInstantiable(c);
          return (T) newInstance.invoke(null, c, ctorId);
        }
      };
    } catch (Throwable ignored) {
    }

    // 3) ObjectInputStream.newInstance (Android pre‐Gingerbread)
    try {
      Method newInstance =
          ObjectInputStream.class.getDeclaredMethod("newInstance", Class.class, Class.class);
      newInstance.setAccessible(true);

      return new UnsafeAllocator() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T newInstance(Class<T> c) throws Exception {
          assertInstantiable(c);
          return (T) newInstance.invoke(null, c, Object.class);
        }
      };
    } catch (Throwable ignored) {
    }

    // 4) give up
    return new UnsafeAllocator() {
      @Override
      public <T> T newInstance(Class<T> c) {
        throw new UnsupportedOperationException(
            "Cannot allocate "
                + c
                + ". Ensure your runtime supports one of the UnsafeAllocator mechanisms.");
      }
    };
  }
}
