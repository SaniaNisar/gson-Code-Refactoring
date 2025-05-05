/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * A type adapter wrapper that handles polymorphic type resolution at runtime.
 *
 * @param <T> The type to adapt.
 */
final class TypeAdapterRuntimeTypeWrapper<T> extends TypeAdapter<T> {
  private final Gson context;
  private final TypeAdapter<T> delegate;
  private final Type declaredType;

  /**
   * Creates a new runtime type wrapper for the given delegate adapter.
   *
   * @param context The Gson context
   * @param delegate The delegate type adapter
   * @param declaredType The declared type
   */
  TypeAdapterRuntimeTypeWrapper(Gson context, TypeAdapter<T> delegate, Type declaredType) {
    this.context = context;
    this.delegate = delegate;
    this.declaredType = declaredType;
  }

  @Override
  public T read(JsonReader in) throws IOException {
    return delegate.read(in);
  }

  @Override
  public void write(JsonWriter out, T value) throws IOException {
    TypeAdapter<T> chosenAdapter = resolveTypeAdapter(value);
    chosenAdapter.write(out, value);
  }

  /**
   * Resolves the most appropriate type adapter based on runtime type information.
   *
   * @param value The value to serialize
   * @return The resolved type adapter
   */
  private TypeAdapter<T> resolveTypeAdapter(T value) {
    // Get the runtime type if it's more specific than the declared type
    Type runtimeType = getRuntimeTypeIfMoreSpecific(declaredType, value);

    if (runtimeType == declaredType) {
      return delegate;
    }

    // Get the adapter for the runtime type
    @SuppressWarnings("unchecked")
    TypeAdapter<T> runtimeTypeAdapter =
        (TypeAdapter<T>) context.getAdapter(TypeToken.get(runtimeType));

    return selectMostAppropriateAdapter(runtimeTypeAdapter);
  }

  /**
   * Selects the most appropriate adapter between the delegate and runtime type adapter.
   *
   * @param runtimeTypeAdapter The adapter for the runtime type
   * @return The most appropriate adapter based on adapter preferences
   */
  private TypeAdapter<T> selectMostAppropriateAdapter(TypeAdapter<T> runtimeTypeAdapter) {
    // For backward compatibility only check ReflectiveTypeAdapterFactory.Adapter here but not any
    // other wrapping adapters, see
    // https://github.com/google/gson/pull/1787#issuecomment-1222175189
    if (!(runtimeTypeAdapter instanceof ReflectiveTypeAdapterFactory.Adapter)) {
      // The user registered a type adapter for the runtime type, so we will use that
      return runtimeTypeAdapter;
    } else if (!isReflective(delegate)) {
      // The user registered a type adapter for Base class, so we prefer it over the
      // reflective type adapter for the runtime type
      return delegate;
    } else {
      // Use the type adapter for runtime type
      return runtimeTypeAdapter;
    }
  }

  /**
   * Returns whether the type adapter uses reflection.
   *
   * @param typeAdapter the type adapter to check.
   * @return true if the adapter is reflective, false otherwise
   */
  private static boolean isReflective(TypeAdapter<?> typeAdapter) {
    TypeAdapter<?> currentAdapter = typeAdapter;

    // Run this in loop in case multiple delegating adapters are nested
    while (currentAdapter instanceof SerializationDelegatingTypeAdapter) {
      SerializationDelegatingTypeAdapter<?> delegatingAdapter =
          (SerializationDelegatingTypeAdapter<?>) currentAdapter;
      TypeAdapter<?> delegate = delegatingAdapter.getSerializationDelegate();

      // Break if adapter does not delegate serialization
      if (delegate == currentAdapter) {
        break;
      }
      currentAdapter = delegate;
    }

    return currentAdapter instanceof ReflectiveTypeAdapterFactory.Adapter;
  }

  /**
   * Finds a compatible runtime type if it is more specific than the declared type.
   *
   * @param type The declared type
   * @param value The object value
   * @return The runtime type if more specific, otherwise the declared type
   */
  private static Type getRuntimeTypeIfMoreSpecific(Type type, Object value) {
    if (value != null && (type instanceof Class<?> || type instanceof TypeVariable<?>)) {
      return value.getClass();
    }
    return type;
  }
}
