/*
 * Copyright (C) 2024 Google Inc.
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

package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Adapter for enum classes (but not for the base class {@code java.lang.Enum}). */
class EnumTypeAdapter<T extends Enum<T>> extends TypeAdapter<T> {

  /** Factory for creating EnumTypeAdapter instances. */
  static final TypeAdapterFactory FACTORY = new EnumTypeAdapterFactory();

  private final EnumMapping<T> enumMapping;

  private EnumTypeAdapter(Class<T> enumClass) {
    this.enumMapping = EnumMapping.create(enumClass);
  }

  @Override
  public T read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    String key = in.nextString();
    return enumMapping
        .lookupBySerializedNameOrToString(key)
        .orElse(null); // Return null if no matching constant found
  }

  @Override
  public void write(JsonWriter out, T value) throws IOException {
    out.value(value == null ? null : enumMapping.getSerializedName(value));
  }

  /** Factory for creating EnumTypeAdapter instances. */
  private static class EnumTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
      Class<? super T> rawType = typeToken.getRawType();

      if (!isEnumType(rawType)) {
        return null;
      }

      Class<? super T> enumClass = getEnumClass(rawType);

      @SuppressWarnings({"rawtypes", "unchecked"})
      TypeAdapter<T> adapter = (TypeAdapter<T>) new EnumTypeAdapter(enumClass);
      return adapter;
    }

    private boolean isEnumType(Class<?> type) {
      return Enum.class.isAssignableFrom(type) && type != Enum.class;
    }

    private <T> Class<? super T> getEnumClass(Class<? super T> type) {
      if (!type.isEnum()) {
        return type.getSuperclass(); // Handle anonymous subclasses
      }
      return type;
    }
  }

  /** Encapsulates the mapping between enum constants and their serialized names. */
  private static class EnumMapping<T extends Enum<T>> {
    private final Map<String, T> nameToConstant = new HashMap<>();
    private final Map<String, T> stringToConstant = new HashMap<>();
    private final Map<T, String> constantToName = new HashMap<>();

    /** Creates an EnumMapping for the specified enum class. */
    static <T extends Enum<T>> EnumMapping<T> create(Class<T> enumClass) {
      EnumMapping<T> mapping = new EnumMapping<>();
      mapping.initializeMapping(enumClass);
      return mapping;
    }

    /** Initializes the mapping between enum constants and their serialized names. */
    private void initializeMapping(Class<T> enumClass) {
      try {
        Field[] enumConstants = getEnumConstantFields(enumClass);
        AccessibleObject.setAccessible(enumConstants, true);

        for (Field constantField : enumConstants) {
          @SuppressWarnings("unchecked")
          T constant = (T) constantField.get(null);

          // Map the default name
          String name = constant.name();
          String toStringValue = constant.toString();

          // Check for SerializedName annotation
          SerializedName annotation = constantField.getAnnotation(SerializedName.class);
          if (annotation != null) {
            name = annotation.value();
            mapAlternateNames(constant, annotation);
          }

          nameToConstant.put(name, constant);
          stringToConstant.put(toStringValue, constant);
          constantToName.put(constant, name);
        }
      } catch (IllegalAccessException e) {
        // Should be impossible due to setAccessible call
        throw new AssertionError("Failed to access enum constants", e);
      }
    }

    /** Maps alternate names from SerializedName annotation to the enum constant. */
    private void mapAlternateNames(T constant, SerializedName annotation) {
      for (String alternate : annotation.alternate()) {
        nameToConstant.put(alternate, constant);
      }
    }

    /** Gets the enum constant fields for the specified enum class. */
    private Field[] getEnumConstantFields(Class<T> enumClass) {
      Field[] fields = enumClass.getDeclaredFields();
      int constantCount = 0;

      // Filter out non-constant fields, replacing elements as we go
      for (Field f : fields) {
        if (f.isEnumConstant()) {
          fields[constantCount++] = f;
        }
      }

      // Trim the array to the new length
      return Arrays.copyOf(fields, constantCount);
    }

    /** Looks up an enum constant by its serialized name or toString value. */
    Optional<T> lookupBySerializedNameOrToString(String key) {
      T constant = nameToConstant.get(key);
      if (constant != null) {
        return Optional.of(constant);
      }

      return Optional.ofNullable(stringToConstant.get(key));
    }

    /** Gets the serialized name for an enum constant. */
    String getSerializedName(T constant) {
      return constantToName.get(constant);
    }
  }
}
