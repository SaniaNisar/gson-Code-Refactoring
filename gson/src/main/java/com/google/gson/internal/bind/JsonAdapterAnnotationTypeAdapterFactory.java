package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.reflect.TypeToken;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This factory looks for @JsonAdapter annotations and uses the specified class as the default type
 * adapter.
 */
public final class JsonAdapterAnnotationTypeAdapterFactory implements TypeAdapterFactory {

  private static final class DummyTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      throw new AssertionError("Factory should not be used");
    }
  }

  private static final TypeAdapterFactory TREE_TYPE_CLASS_DUMMY_FACTORY =
      new DummyTypeAdapterFactory();
  private static final TypeAdapterFactory TREE_TYPE_FIELD_DUMMY_FACTORY =
      new DummyTypeAdapterFactory();

  private final ConstructorConstructor constructorConstructor;
  private final ConcurrentMap<Class<?>, TypeAdapterFactory> adapterFactoryMap;

  public JsonAdapterAnnotationTypeAdapterFactory(ConstructorConstructor constructorConstructor) {
    this.constructorConstructor = constructorConstructor;
    this.adapterFactoryMap = new ConcurrentHashMap<>();
  }

  // Helper method to retrieve @JsonAdapter annotation
  private static JsonAdapter getAnnotation(Class<?> rawType) {
    return rawType.getAnnotation(JsonAdapter.class);
  }

  /**
   * Creates a TypeAdapter for a given type based on @JsonAdapter annotation.
   *
   * @param gson Gson instance used for deserialization
   * @param targetType TypeToken representing the target type to adapt
   * @return The TypeAdapter or null if no @JsonAdapter is found
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> targetType) {
    Class<? super T> rawType = targetType.getRawType();
    JsonAdapter annotation = getAnnotation(rawType);

    if (annotation == null) {
      return null;
    }

    return (TypeAdapter<T>)
        getTypeAdapter(constructorConstructor, gson, targetType, annotation, true);
  }

  // Creates an adapter instance using ConstructorConstructor
  private static Object createAdapter(
      ConstructorConstructor constructorConstructor, Class<?> adapterClass) {
    boolean allowUnsafe = true;
    return constructorConstructor.get(TypeToken.get(adapterClass), allowUnsafe).construct();
  }

  /** Retrieves or creates the appropriate TypeAdapter for a given type and annotation. */
  TypeAdapter<?> getTypeAdapter(
      ConstructorConstructor constructorConstructor,
      Gson gson,
      TypeToken<?> type,
      JsonAdapter annotation,
      boolean isClassAnnotation) {

    Object instance = createAdapter(constructorConstructor, annotation.value());
    TypeAdapter<?> typeAdapter = null;
    boolean nullSafe = annotation.nullSafe();

    // Check if instance is an actual TypeAdapter
    if (instance instanceof TypeAdapter) {
      typeAdapter = (TypeAdapter<?>) instance;
    }
    // If it's a TypeAdapterFactory, create a new adapter
    else if (instance instanceof TypeAdapterFactory) {
      TypeAdapterFactory factory = (TypeAdapterFactory) instance;
      if (isClassAnnotation) {
        factory = putFactoryAndGetCurrent(type.getRawType(), factory);
      }
      typeAdapter = factory.create(gson, type);
    }
    // If it's a JsonSerializer or JsonDeserializer, use TreeTypeAdapter
    else if (instance instanceof JsonSerializer || instance instanceof JsonDeserializer) {
      JsonSerializer<?> serializer =
          instance instanceof JsonSerializer ? (JsonSerializer<?>) instance : null;
      JsonDeserializer<?> deserializer =
          instance instanceof JsonDeserializer ? (JsonDeserializer<?>) instance : null;

      TypeAdapterFactory skipPast =
          isClassAnnotation ? TREE_TYPE_CLASS_DUMMY_FACTORY : TREE_TYPE_FIELD_DUMMY_FACTORY;
      typeAdapter = new TreeTypeAdapter(serializer, deserializer, gson, type, skipPast, nullSafe);
      nullSafe = false; // TreeTypeAdapter already handles null safety
    } else {
      throw new IllegalArgumentException(
          "Invalid attempt to bind an instance of "
              + instance.getClass().getName()
              + " as a @JsonAdapter for "
              + type.getRawType().getName()
              + ". @JsonAdapter value must be a TypeAdapter, TypeAdapterFactory, JsonSerializer or"
              + " JsonDeserializer.");
    }

    if (typeAdapter != null && nullSafe) {
      typeAdapter = typeAdapter.nullSafe();
    }

    return typeAdapter;
  }

  /** Registers the adapter factory for a given raw type if not already present. */
  private TypeAdapterFactory putFactoryAndGetCurrent(Class<?> rawType, TypeAdapterFactory factory) {
    TypeAdapterFactory existingFactory = adapterFactoryMap.putIfAbsent(rawType, factory);
    return existingFactory != null ? existingFactory : factory;
  }

  /** Checks if the factory was created for @JsonAdapter on a class. */
  public boolean isClassJsonAdapterFactory(TypeToken<?> type, TypeAdapterFactory factory) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(factory);

    if (factory == TREE_TYPE_CLASS_DUMMY_FACTORY) {
      return true;
    }

    Class<?> rawType = type.getRawType();
    TypeAdapterFactory existingFactory = adapterFactoryMap.get(rawType);

    if (existingFactory != null) {
      return existingFactory == factory;
    }

    JsonAdapter annotation = getAnnotation(rawType);
    if (annotation == null) {
      return false;
    }

    Class<?> adapterClass = annotation.value();
    if (!TypeAdapterFactory.class.isAssignableFrom(adapterClass)) {
      return false;
    }

    Object adapter = createAdapter(constructorConstructor, adapterClass);
    TypeAdapterFactory newFactory = (TypeAdapterFactory) adapter;

    return putFactoryAndGetCurrent(rawType, newFactory) == factory;
  }
}
