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
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.GsonTypes;
import com.google.gson.internal.JsonReaderInternalAccess;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapts maps to either JSON objects or JSON arrays.
 *
 * <h2>Maps as JSON objects</h2>
 *
 * For primitive keys or when complex map key serialization is not enabled, this converts Java
 * {@link Map Maps} to JSON Objects. This requires that map keys can be serialized as strings; this
 * is insufficient for some key types. For example, consider a map whose keys are points on a grid.
 * The default JSON form encodes reasonably:
 *
 * <pre>{@code
 * Map<Point, String> original = new LinkedHashMap<>();
 * original.put(new Point(5, 6), "a");
 * original.put(new Point(8, 8), "b");
 * System.out.println(gson.toJson(original, type));
 * }</pre>
 *
 * The above code prints this JSON object:
 *
 * <pre>{@code
 * {
 *   "(5,6)": "a",
 *   "(8,8)": "b"
 * }
 * }</pre>
 *
 * But GSON is unable to deserialize this value because the JSON string name is just the {@link
 * Object#toString() toString()} of the map key. Attempting to convert the above JSON to an object
 * fails with a parse exception:
 *
 * <pre>com.google.gson.JsonParseException: Expecting object found: "(5,6)"
 *   at com.google.gson.JsonObjectDeserializationVisitor.visitFieldUsingCustomHandler
 *   at com.google.gson.ObjectNavigator.navigateClassFields
 *   ...</pre>
 *
 * <h2>Maps as JSON arrays</h2>
 *
 * An alternative approach taken by this type adapter when it is required and complex map key
 * serialization is enabled is to encode maps as arrays of map entries. Each map entry is a two
 * element array containing a key and a value. This approach is more flexible because any type can
 * be used as the map's key; not just strings. But it's also less portable because the receiver of
 * such JSON must be aware of the map entry convention.
 *
 * <p>Register this adapter when you are creating your GSON instance.
 *
 * <pre>{@code
 * Gson gson = new GsonBuilder()
 *   .registerTypeAdapter(Map.class, new MapAsArrayTypeAdapter())
 *   .create();
 * }</pre>
 *
 * This will change the structure of the JSON emitted by the code above. Now we get an array. In
 * this case the arrays elements are map entries:
 *
 * <pre>{@code
 * [
 *   [
 *     {
 *       "x": 5,
 *       "y": 6
 *     },
 *     "a",
 *   ],
 *   [
 *     {
 *       "x": 8,
 *       "y": 8
 *     },
 *     "b"
 *   ]
 * ]
 * }</pre>
 *
 * This format will serialize and deserialize just fine as long as this adapter is registered.
 */
public final class MapTypeAdapterFactory implements TypeAdapterFactory {
  private final ConstructorConstructor constructorConstructor;
  final boolean complexMapKeySerialization;

  /** Encapsulates map type information to reduce parameter passing. */
  private static class MapTypeInfo<T> {
    final TypeAdapter<?> keyTypeAdapter;
    final TypeAdapter<?> valueTypeAdapter;
    final ObjectConstructor<? extends T> constructor;

    MapTypeInfo(
        TypeAdapter<?> keyTypeAdapter,
        TypeAdapter<?> valueTypeAdapter,
        ObjectConstructor<? extends T> constructor) {
      this.keyTypeAdapter = keyTypeAdapter;
      this.valueTypeAdapter = valueTypeAdapter;
      this.constructor = constructor;
    }
  }

  public MapTypeAdapterFactory(
      ConstructorConstructor constructorConstructor, boolean complexMapKeySerialization) {
    this.constructorConstructor = constructorConstructor;
    this.complexMapKeySerialization = complexMapKeySerialization;
  }

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
    Type type = typeToken.getType();

    Class<? super T> rawType = typeToken.getRawType();
    if (!Map.class.isAssignableFrom(rawType)) {
      return null;
    }

    MapTypeInfo<? extends Map<?, ?>> mapTypeInfo = createMapTypeInfo(gson, type, rawType);

    @SuppressWarnings({"unchecked", "rawtypes"})
    // we don't define a type parameter for the key or value types
    TypeAdapter<T> result =
        new Adapter(
            mapTypeInfo.keyTypeAdapter, mapTypeInfo.valueTypeAdapter, mapTypeInfo.constructor);

    return result;
  }

  /** Creates type information for map handling. */
  private <T> MapTypeInfo<T> createMapTypeInfo(Gson gson, Type type, Class<?> rawType) {
    Type[] keyAndValueTypes = GsonTypes.getMapKeyAndValueTypes(type, rawType);
    Type keyType = keyAndValueTypes[0];
    Type valueType = keyAndValueTypes[1];

    TypeAdapter<?> keyAdapter = getKeyAdapter(gson, keyType);
    TypeAdapter<?> wrappedKeyAdapter =
        new TypeAdapterRuntimeTypeWrapper<>(gson, keyAdapter, keyType);
    TypeAdapter<?> valueAdapter = gson.getAdapter(TypeToken.get(valueType));
    TypeAdapter<?> wrappedValueAdapter =
        new TypeAdapterRuntimeTypeWrapper<>(gson, valueAdapter, valueType);

    // Don't allow Unsafe usage to create instance; instances might be in broken state and calling
    // Map methods could lead to confusing exceptions
    boolean allowUnsafe = false;
    @SuppressWarnings("unchecked")
    ObjectConstructor<T> constructor =
        (ObjectConstructor<T>) constructorConstructor.get(TypeToken.get(type), allowUnsafe);

    return new MapTypeInfo<>(wrappedKeyAdapter, wrappedValueAdapter, constructor);
  }

  /** Returns a type adapter that writes the value as a string. */
  private TypeAdapter<?> getKeyAdapter(Gson context, Type keyType) {
    return (keyType == boolean.class || keyType == Boolean.class)
        ? TypeAdapters.BOOLEAN_AS_STRING
        : context.getAdapter(TypeToken.get(keyType));
  }

  private final class Adapter<K, V> extends TypeAdapter<Map<K, V>> {
    private final TypeAdapter<K> keyTypeAdapter;
    private final TypeAdapter<V> valueTypeAdapter;
    private final ObjectConstructor<? extends Map<K, V>> constructor;
    private final KeyStringConverter keyConverter;

    /** Holds data needed for map serialization. */
    private class MapSerializationInfo {
      final List<JsonElement> keys;
      final List<V> values;
      final boolean hasComplexKeys;

      MapSerializationInfo(List<JsonElement> keys, List<V> values, boolean hasComplexKeys) {
        this.keys = keys;
        this.values = values;
        this.hasComplexKeys = hasComplexKeys;
      }
    }

    /** Handles conversion of JsonElement keys to strings. */
    private class KeyStringConverter {
      /** Converts a JsonElement to a string representation for use as a key. */
      String keyToString(JsonElement keyElement) {
        if (keyElement.isJsonPrimitive()) {
          return primitiveKeyToString(keyElement.getAsJsonPrimitive());
        } else if (keyElement.isJsonNull()) {
          return "null";
        } else {
          throw new AssertionError();
        }
      }

      /** Converts a JsonPrimitive to a string representation. */
      private String primitiveKeyToString(JsonPrimitive primitive) {
        if (primitive.isNumber()) {
          return String.valueOf(primitive.getAsNumber());
        } else if (primitive.isBoolean()) {
          return Boolean.toString(primitive.getAsBoolean());
        } else if (primitive.isString()) {
          return primitive.getAsString();
        } else {
          throw new AssertionError();
        }
      }
    }

    public Adapter(
        TypeAdapter<K> keyTypeAdapter,
        TypeAdapter<V> valueTypeAdapter,
        ObjectConstructor<? extends Map<K, V>> constructor) {
      this.keyTypeAdapter = keyTypeAdapter;
      this.valueTypeAdapter = valueTypeAdapter;
      this.constructor = constructor;
      this.keyConverter = new KeyStringConverter();
    }

    @Override
    public Map<K, V> read(JsonReader in) throws IOException {
      JsonToken peek = in.peek();
      if (peek == JsonToken.NULL) {
        in.nextNull();
        return null;
      }

      Map<K, V> map = constructor.construct();

      if (peek == JsonToken.BEGIN_ARRAY) {
        readMapFromArray(in, map);
      } else {
        readMapFromObject(in, map);
      }

      return map;
    }

    /** Reads a map from a JSON array format. */
    private void readMapFromArray(JsonReader in, Map<K, V> map) throws IOException {
      in.beginArray();
      while (in.hasNext()) {
        in.beginArray(); // entry array
        K key = keyTypeAdapter.read(in);
        V value = valueTypeAdapter.read(in);
        putKeyValueInMap(map, key, value);
        in.endArray();
      }
      in.endArray();
    }

    /** Reads a map from a JSON object format. */
    private void readMapFromObject(JsonReader in, Map<K, V> map) throws IOException {
      in.beginObject();
      while (in.hasNext()) {
        JsonReaderInternalAccess.INSTANCE.promoteNameToValue(in);
        K key = keyTypeAdapter.read(in);
        V value = valueTypeAdapter.read(in);
        putKeyValueInMap(map, key, value);
      }
      in.endObject();
    }

    /** Puts a key-value pair in the map, throwing an exception for duplicate keys. */
    private void putKeyValueInMap(Map<K, V> map, K key, V value) {
      V replaced = map.put(key, value);
      if (replaced != null) {
        throw new JsonSyntaxException("duplicate key: " + key);
      }
    }

    @Override
    public void write(JsonWriter out, Map<K, V> map) throws IOException {
      if (map == null) {
        out.nullValue();
        return;
      }

      if (!complexMapKeySerialization) {
        writeMapAsObject(out, map);
        return;
      }

      MapSerializationInfo serInfo = prepareMapSerialization(map);

      if (serInfo.hasComplexKeys) {
        writeMapAsArray(out, serInfo);
      } else {
        writeMapAsSimpleObject(out, serInfo);
      }
    }

    /** Writes map as a simple object with non-complex keys. */
    private void writeMapAsObject(JsonWriter out, Map<K, V> map) throws IOException {
      out.beginObject();
      for (Map.Entry<K, V> entry : map.entrySet()) {
        out.name(String.valueOf(entry.getKey()));
        valueTypeAdapter.write(out, entry.getValue());
      }
      out.endObject();
    }

    /** Prepares serialization data for a map. */
    private MapSerializationInfo prepareMapSerialization(Map<K, V> map) {
      boolean hasComplexKeys = false;
      List<JsonElement> keys = new ArrayList<>(map.size());
      List<V> values = new ArrayList<>(map.size());

      for (Map.Entry<K, V> entry : map.entrySet()) {
        JsonElement keyElement = keyTypeAdapter.toJsonTree(entry.getKey());
        keys.add(keyElement);
        values.add(entry.getValue());
        hasComplexKeys |= keyElement.isJsonArray() || keyElement.isJsonObject();
      }

      return new MapSerializationInfo(keys, values, hasComplexKeys);
    }

    /** Writes a map as an array of key-value pairs. */
    private void writeMapAsArray(JsonWriter out, MapSerializationInfo serInfo) throws IOException {
      out.beginArray();
      for (int i = 0, size = serInfo.keys.size(); i < size; i++) {
        out.beginArray(); // entry array
        Streams.write(serInfo.keys.get(i), out);
        valueTypeAdapter.write(out, serInfo.values.get(i));
        out.endArray();
      }
      out.endArray();
    }

    /** Writes a map as a regular object with simple keys. */
    private void writeMapAsSimpleObject(JsonWriter out, MapSerializationInfo serInfo)
        throws IOException {
      out.beginObject();
      for (int i = 0, size = serInfo.keys.size(); i < size; i++) {
        JsonElement keyElement = serInfo.keys.get(i);
        out.name(keyConverter.keyToString(keyElement));
        valueTypeAdapter.write(out, serInfo.values.get(i));
      }
      out.endObject();
    }
  }
}
