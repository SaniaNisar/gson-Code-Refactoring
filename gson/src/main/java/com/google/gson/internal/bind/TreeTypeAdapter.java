package com.google.gson.internal.bind;

import com.google.gson.*;
import com.google.gson.internal.GsonPreconditions;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * TreeTypeAdapter allows combining a JsonSerializer and/or JsonDeserializer into a streaming
 * TypeAdapter.
 */
public final class TreeTypeAdapter<T> extends SerializationDelegatingTypeAdapter<T> {

  private final JsonSerializer<T> serializer;
  private final JsonDeserializer<T> deserializer;
  private final Gson gson;
  private final TypeToken<T> typeToken;
  private final TypeAdapterFactory skipPastFactory;
  private final boolean nullSafe;

  private volatile TypeAdapter<T> delegate;
  private final GsonContextImpl context = new GsonContextImpl();

  public TreeTypeAdapter(
      JsonSerializer<T> serializer,
      JsonDeserializer<T> deserializer,
      Gson gson,
      TypeToken<T> typeToken,
      TypeAdapterFactory skipPastFactory,
      boolean nullSafe) {
    this.serializer = serializer;
    this.deserializer = deserializer;
    this.gson = gson;
    this.typeToken = typeToken;
    this.skipPastFactory = skipPastFactory;
    this.nullSafe = nullSafe;
  }

  public TreeTypeAdapter(
      JsonSerializer<T> serializer,
      JsonDeserializer<T> deserializer,
      Gson gson,
      TypeToken<T> typeToken,
      TypeAdapterFactory skipPastFactory) {
    this(serializer, deserializer, gson, typeToken, skipPastFactory, true);
  }

  @Override
  public T read(JsonReader in) throws IOException {
    if (deserializer == null) {
      return delegate().read(in);
    }

    JsonElement jsonElement = Streams.parse(in);
    if (nullSafe && jsonElement.isJsonNull()) {
      return null;
    }

    return deserializer.deserialize(jsonElement, typeToken.getType(), context);
  }

  @Override
  public void write(JsonWriter out, T value) throws IOException {
    if (serializer == null) {
      delegate().write(out, value);
      return;
    }

    if (nullSafe && value == null) {
      out.nullValue();
      return;
    }

    JsonElement jsonTree = serializer.serialize(value, typeToken.getType(), context);
    Streams.write(jsonTree, out);
  }

  @Override
  public TypeAdapter<T> getSerializationDelegate() {
    return (serializer != null) ? this : delegate();
  }

  private TypeAdapter<T> delegate() {
    TypeAdapter<T> d = delegate;
    if (d == null) {
      d = delegate = gson.getDelegateAdapter(skipPastFactory, typeToken);
    }
    return d;
  }

  public static TypeAdapterFactory newFactory(TypeToken<?> exactType, Object typeAdapter) {
    return new SingleTypeFactory(typeAdapter, exactType, false, null);
  }

  public static TypeAdapterFactory newFactoryWithMatchRawType(
      TypeToken<?> exactType, Object typeAdapter) {
    boolean matchRawType = exactType.getType() == exactType.getRawType();
    return new SingleTypeFactory(typeAdapter, exactType, matchRawType, null);
  }

  public static TypeAdapterFactory newTypeHierarchyFactory(
      Class<?> hierarchyType, Object typeAdapter) {
    return new SingleTypeFactory(typeAdapter, null, false, hierarchyType);
  }

  private static final class SingleTypeFactory implements TypeAdapterFactory {

    private final TypeToken<?> exactType;
    private final boolean matchRawType;
    private final Class<?> hierarchyType;
    private final JsonSerializer<?> serializer;
    private final JsonDeserializer<?> deserializer;

    SingleTypeFactory(
        Object typeAdapter, TypeToken<?> exactType, boolean matchRawType, Class<?> hierarchyType) {
      this.serializer =
          typeAdapter instanceof JsonSerializer ? (JsonSerializer<?>) typeAdapter : null;
      this.deserializer =
          typeAdapter instanceof JsonDeserializer ? (JsonDeserializer<?>) typeAdapter : null;
      GsonPreconditions.checkArgument(serializer != null || deserializer != null);
      this.exactType = exactType;
      this.matchRawType = matchRawType;
      this.hierarchyType = hierarchyType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      boolean matches =
          exactType != null
              ? exactType.equals(type) || (matchRawType && exactType.getType() == type.getRawType())
              : hierarchyType != null && hierarchyType.isAssignableFrom(type.getRawType());

      return matches
          ? new TreeTypeAdapter<>(
              (JsonSerializer<T>) serializer, (JsonDeserializer<T>) deserializer, gson, type, this)
          : null;
    }
  }

  private final class GsonContextImpl
      implements JsonSerializationContext, JsonDeserializationContext {

    @Override
    public JsonElement serialize(Object src) {
      return gson.toJsonTree(src);
    }

    @Override
    public JsonElement serialize(Object src, Type typeOfSrc) {
      return gson.toJsonTree(src, typeOfSrc);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R deserialize(JsonElement json, Type typeOfT) throws JsonParseException {
      return gson.fromJson(json, typeOfT);
    }
  }
}
