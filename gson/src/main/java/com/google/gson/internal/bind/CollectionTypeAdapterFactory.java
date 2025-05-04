package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.GsonTypes;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;

/** Adapt a homogeneous collection of objects. */
public final class CollectionTypeAdapterFactory implements TypeAdapterFactory {

  private final ConstructorConstructor constructorProvider;

  public CollectionTypeAdapterFactory(ConstructorConstructor constructorProvider) {
    this.constructorProvider = constructorProvider;
  }

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
    Type type = typeToken.getType();
    Class<? super T> rawType = typeToken.getRawType();

    // If it's not a Collection, return null
    if (!Collection.class.isAssignableFrom(rawType)) {
      return null;
    }

    // Get the element type inside the Collection
    Type elementType = GsonTypes.getCollectionElementType(type, rawType);
    @SuppressWarnings("unchecked")
    TypeToken<Object> elementTypeToken = (TypeToken<Object>) TypeToken.get(elementType);

    // Get the element type adapter and wrap it
    TypeAdapter<Object> elementTypeAdapter = gson.getAdapter(elementTypeToken);
    TypeAdapter<Object> wrappedAdapter =
        new TypeAdapterRuntimeTypeWrapper<>(gson, elementTypeAdapter, elementType);

    // Get the ObjectConstructor for the Collection<E>
    @SuppressWarnings("unchecked")
    ObjectConstructor<? extends Collection<Object>> constructor =
        (ObjectConstructor<? extends Collection<Object>>) constructorProvider.get(typeToken, false);

    // Return the Adapter wrapped with nullSafe
    @SuppressWarnings("unchecked")
    TypeAdapter<T> result = (TypeAdapter<T>) new Adapter<>(wrappedAdapter, constructor).nullSafe();

    return result;
  }

  /** Adapter to read/write collections using the element type adapter and constructor. */
  private static final class Adapter<E> extends TypeAdapter<Collection<E>> {
    private final TypeAdapter<E> elementTypeAdapter;
    private final ObjectConstructor<? extends Collection<E>> constructor;

    public Adapter(
        TypeAdapter<E> elementTypeAdapter, ObjectConstructor<? extends Collection<E>> constructor) {
      this.elementTypeAdapter = elementTypeAdapter;
      this.constructor = constructor;
    }

    @Override
    public Collection<E> read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }

      Collection<E> collection = constructor.construct();
      in.beginArray();
      while (in.hasNext()) {
        E instance = elementTypeAdapter.read(in);
        collection.add(instance);
      }
      in.endArray();
      return collection;
    }

    @Override
    public void write(JsonWriter out, Collection<E> collection) throws IOException {
      if (collection == null) {
        out.nullValue();
        return;
      }

      out.beginArray();
      for (E element : collection) {
        elementTypeAdapter.write(out, element);
      }
      out.endArray();
    }
  }
}
