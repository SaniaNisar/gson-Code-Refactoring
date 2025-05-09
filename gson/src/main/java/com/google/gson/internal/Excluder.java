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

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import com.google.gson.internal.reflect.ReflectionHelper;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

public final class Excluder implements TypeAdapterFactory, Cloneable {
  private static final double IGNORE_VERSIONS = -1.0d;
  public static final Excluder DEFAULT = new Excluder();

  private final double version;
  private final int modifiers;
  private final boolean serializeInnerClasses;
  private final boolean requireExpose;
  private final List<ExclusionStrategy> serializationStrategies;
  private final List<ExclusionStrategy> deserializationStrategies;

  public Excluder() {
    this(
        IGNORE_VERSIONS,
        Modifier.STATIC | Modifier.TRANSIENT,
        true,
        false,
        Collections.emptyList(),
        Collections.emptyList());
  }

  private Excluder(
      double version,
      int modifiers,
      boolean serializeInnerClasses,
      boolean requireExpose,
      List<ExclusionStrategy> serializationStrategies,
      List<ExclusionStrategy> deserializationStrategies) {
    this.version = version;
    this.modifiers = modifiers;
    this.serializeInnerClasses = serializeInnerClasses;
    this.requireExpose = requireExpose;
    this.serializationStrategies = serializationStrategies;
    this.deserializationStrategies = deserializationStrategies;
  }

  public Excluder withVersion(double version) {
    return new Excluder(
        version,
        modifiers,
        serializeInnerClasses,
        requireExpose,
        serializationStrategies,
        deserializationStrategies);
  }

  public Excluder withModifiers(int... modifiers) {
    int mod = 0;
    for (int m : modifiers) mod |= m;
    return new Excluder(
        version,
        mod,
        serializeInnerClasses,
        requireExpose,
        serializationStrategies,
        deserializationStrategies);
  }

  public Excluder disableInnerClassSerialization() {
    return new Excluder(
        version,
        modifiers,
        false,
        requireExpose,
        serializationStrategies,
        deserializationStrategies);
  }

  public Excluder excludeFieldsWithoutExposeAnnotation() {
    return new Excluder(
        version,
        modifiers,
        serializeInnerClasses,
        true,
        serializationStrategies,
        deserializationStrategies);
  }

  public Excluder withExclusionStrategy(
      ExclusionStrategy strategy, boolean serialize, boolean deserialize) {
    List<ExclusionStrategy> newSerialize = serializationStrategies;
    List<ExclusionStrategy> newDeserialize = deserializationStrategies;

    if (serialize) {
      newSerialize = new ArrayList<>(serializationStrategies);
      newSerialize.add(strategy);
    }
    if (deserialize) {
      newDeserialize = new ArrayList<>(deserializationStrategies);
      newDeserialize.add(strategy);
    }

    return new Excluder(
        version, modifiers, serializeInnerClasses, requireExpose, newSerialize, newDeserialize);
  }

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    Class<?> rawType = type.getRawType();
    boolean skipSerialize = excludeClass(rawType, true);
    boolean skipDeserialize = excludeClass(rawType, false);

    if (!skipSerialize && !skipDeserialize) return null;

    return new ExcluderTypeAdapter<>(gson, type, this, skipSerialize, skipDeserialize);
  }

  public boolean excludeField(Field field, boolean serialize) {
    if ((modifiers & field.getModifiers()) != 0) return true;
    if (version != IGNORE_VERSIONS
        && !isValidVersion(field.getAnnotation(Since.class), field.getAnnotation(Until.class)))
      return true;
    if (field.isSynthetic()) return true;
    if (requireExpose) {
      Expose expose = field.getAnnotation(Expose.class);
      if (expose == null || (serialize ? !expose.serialize() : !expose.deserialize())) return true;
    }
    if (excludeClass(field.getType(), serialize)) return true;
    return shouldSkipField(field, serialize);
  }

  private boolean shouldSkipField(Field field, boolean serialize) {
    List<ExclusionStrategy> strategies =
        serialize ? serializationStrategies : deserializationStrategies;
    if (strategies.isEmpty()) return false;

    FieldAttributes attributes = new FieldAttributes(field);
    for (ExclusionStrategy strategy : strategies) {
      if (strategy.shouldSkipField(attributes)) return true;
    }
    return false;
  }

  public boolean excludeClass(Class<?> clazz, boolean serialize) {
    if (version != IGNORE_VERSIONS
        && !isValidVersion(clazz.getAnnotation(Since.class), clazz.getAnnotation(Until.class)))
      return true;
    if (!serializeInnerClasses && isInnerClass(clazz)) return true;
    if (!serialize
        && !Enum.class.isAssignableFrom(clazz)
        && ReflectionHelper.isAnonymousOrNonStaticLocal(clazz)) return true;

    List<ExclusionStrategy> strategies =
        serialize ? serializationStrategies : deserializationStrategies;
    for (ExclusionStrategy strategy : strategies) {
      if (strategy.shouldSkipClass(clazz)) return true;
    }

    return false;
  }

  private static boolean isInnerClass(Class<?> clazz) {
    return clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers());
  }

  private boolean isValidVersion(Since since, Until until) {
    return isValidSince(since) && isValidUntil(until);
  }

  private boolean isValidSince(Since annotation) {
    return annotation == null || version >= annotation.value();
  }

  private boolean isValidUntil(Until annotation) {
    return annotation == null || version < annotation.value();
  }

  private static final class ExcluderTypeAdapter<T> extends TypeAdapter<T> {
    private final Gson gson;
    private final TypeToken<T> type;
    private final Excluder excluder;
    private final boolean skipSerialize;
    private final boolean skipDeserialize;
    private volatile TypeAdapter<T> delegate;

    ExcluderTypeAdapter(
        Gson gson,
        TypeToken<T> type,
        Excluder excluder,
        boolean skipSerialize,
        boolean skipDeserialize) {
      this.gson = gson;
      this.type = type;
      this.excluder = excluder;
      this.skipSerialize = skipSerialize;
      this.skipDeserialize = skipDeserialize;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
      if (skipSerialize) {
        out.nullValue();
        return;
      }
      delegate().write(out, value);
    }

    @Override
    public T read(JsonReader in) throws IOException {
      if (skipDeserialize) {
        in.skipValue();
        return null;
      }
      return delegate().read(in);
    }

    private TypeAdapter<T> delegate() {
      TypeAdapter<T> d = delegate;
      if (d == null) {
        d = delegate = gson.getDelegateAdapter(excluder, type);
      }
      return d;
    }
  }
}
