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
 * TreeTypeAdapter combines a JsonSerializer and/or JsonDeserializer into a streaming TypeAdapter.
 * This class provides a bridge between the tree and streaming models of JSON processing.
 *
 * @param <T> The type to adapt.
 */
public final class TreeTypeAdapter<T> extends SerializationDelegatingTypeAdapter<T> {

  // Core components
  private final JsonSerializer<T> serializer;
  private final JsonDeserializer<T> deserializer;
  private final Gson gson;
  private final TypeToken<T> typeToken;
  private final TypeAdapterFactory skipPastFactory;
  private final boolean nullSafe;

  // Lazy-loaded delegate
  private volatile TypeAdapter<T> delegate;

  // Context implementation for serialization/deserialization
  private final JsonContext context = new JsonContext();

  /**
   * Creates a new TreeTypeAdapter with configuration.
   *
   * @param serializer The JSON serializer to use (may be null)
   * @param deserializer The JSON deserializer to use (may be null)
   * @param gson The Gson instance
   * @param typeToken Type information for T
   * @param skipPastFactory Factory to skip when delegating
   * @param nullSafe Whether nulls should be handled safely
   */
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

  /**
   * Creates a new TreeTypeAdapter with configuration, defaulting to null-safe behavior.
   *
   * @param serializer The JSON serializer to use (may be null)
   * @param deserializer The JSON deserializer to use (may be null)
   * @param gson The Gson instance
   * @param typeToken Type information for T
   * @param skipPastFactory Factory to skip when delegating
   */
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
      return getDelegate().read(in);
    }

    JsonElement jsonElement = Streams.parse(in);
    if (shouldReturnNullForInput(jsonElement)) {
      return null;
    }

    return deserializer.deserialize(jsonElement, typeToken.getType(), context);
  }

  /**
   * Determines if null should be returned based on input element and null-safety setting.
   *
   * @param jsonElement The parsed JSON element
   * @return True if null should be returned
   */
  private boolean shouldReturnNullForInput(JsonElement jsonElement) {
    return nullSafe && jsonElement.isJsonNull();
  }

  @Override
  public void write(JsonWriter out, T value) throws IOException {
    if (serializer == null) {
      getDelegate().write(out, value);
      return;
    }

    if (shouldWriteNullForValue(value)) {
      out.nullValue();
      return;
    }

    JsonElement jsonTree = serializer.serialize(value, typeToken.getType(), context);
    Streams.write(jsonTree, out);
  }

  /**
   * Determines if null should be written based on value and null-safety setting.
   *
   * @param value The value to serialize
   * @return True if null should be written
   */
  private boolean shouldWriteNullForValue(T value) {
    return nullSafe && value == null;
  }

  @Override
  public TypeAdapter<T> getSerializationDelegate() {
    return (serializer != null) ? this : getDelegate();
  }

  /**
   * Gets the delegate adapter, initializing it if needed.
   *
   * @return The delegate TypeAdapter
   */
  private TypeAdapter<T> getDelegate() {
    TypeAdapter<T> d = delegate;
    if (d == null) {
      synchronized (this) {
        d = delegate;
        if (d == null) {
          d = delegate = gson.getDelegateAdapter(skipPastFactory, typeToken);
        }
      }
    }
    return d;
  }

  /**
   * Creates a new factory for an exact type.
   *
   * @param exactType The exact type to match
   * @param typeAdapter The adapter to use
   * @return A new TypeAdapterFactory
   */
  public static TypeAdapterFactory newFactory(TypeToken<?> exactType, Object typeAdapter) {
    return new SingleTypeFactory(typeAdapter, exactType, false, null);
  }

  /**
   * Creates a new factory that can optionally match raw types.
   *
   * @param exactType The exact type to match
   * @param typeAdapter The adapter to use
   * @return A new TypeAdapterFactory
   */
  public static TypeAdapterFactory newFactoryWithMatchRawType(
      TypeToken<?> exactType, Object typeAdapter) {
    boolean matchRawType = exactType.getType() == exactType.getRawType();
    return new SingleTypeFactory(typeAdapter, exactType, matchRawType, null);
  }

  /**
   * Creates a new factory that matches a type hierarchy.
   *
   * @param hierarchyType The base type of the hierarchy
   * @param typeAdapter The adapter to use
   * @return A new TypeAdapterFactory
   */
  public static TypeAdapterFactory newTypeHierarchyFactory(
      Class<?> hierarchyType, Object typeAdapter) {
    return new SingleTypeFactory(typeAdapter, null, false, hierarchyType);
  }

  /** A factory that creates TreeTypeAdapter instances for specific types. */
  private static final class SingleTypeFactory implements TypeAdapterFactory {
    private final TypeToken<?> exactType;
    private final boolean matchRawType;
    private final Class<?> hierarchyType;
    private final JsonSerializer<?> serializer;
    private final JsonDeserializer<?> deserializer;

    /**
     * Creates a new SingleTypeFactory.
     *
     * @param typeAdapter The adapter object (serializer and/or deserializer)
     * @param exactType The exact type to match (may be null)
     * @param matchRawType Whether to match raw types
     * @param hierarchyType The hierarchy type to match (may be null)
     */
    SingleTypeFactory(
        Object typeAdapter, TypeToken<?> exactType, boolean matchRawType, Class<?> hierarchyType) {
      // Extract serializer and deserializer interfaces from the adapter
      this.serializer = extractSerializer(typeAdapter);
      this.deserializer = extractDeserializer(typeAdapter);

      // Validate that at least one interface is implemented
      GsonPreconditions.checkArgument(serializer != null || deserializer != null);

      this.exactType = exactType;
      this.matchRawType = matchRawType;
      this.hierarchyType = hierarchyType;
    }

    /**
     * Extracts a JsonSerializer from an object if it implements the interface.
     *
     * @param typeAdapter The potential serializer object
     * @return The extracted serializer or null
     */
    private JsonSerializer<?> extractSerializer(Object typeAdapter) {
      return typeAdapter instanceof JsonSerializer ? (JsonSerializer<?>) typeAdapter : null;
    }

    /**
     * Extracts a JsonDeserializer from an object if it implements the interface.
     *
     * @param typeAdapter The potential deserializer object
     * @return The extracted deserializer or null
     */
    private JsonDeserializer<?> extractDeserializer(Object typeAdapter) {
      return typeAdapter instanceof JsonDeserializer ? (JsonDeserializer<?>) typeAdapter : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      if (!matchesType(type)) {
        return null;
      }

      return new TreeTypeAdapter<>(
          (JsonSerializer<T>) serializer, (JsonDeserializer<T>) deserializer, gson, type, this);
    }

    /**
     * Determines if the factory matches the given type.
     *
     * @param type The type to check
     * @return True if the factory matches the type
     */
    private boolean matchesType(TypeToken<?> type) {
      if (exactType != null) {
        return exactType.equals(type) || (matchRawType && exactType.getType() == type.getRawType());
      }

      return hierarchyType != null && hierarchyType.isAssignableFrom(type.getRawType());
    }
  }

  /**
   * Implementation of JsonSerializationContext and JsonDeserializationContext. Delegates operations
   * to the parent Gson instance.
   */
  private final class JsonContext implements JsonSerializationContext, JsonDeserializationContext {
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
