package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.GsonTypes;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Adapter for handling JSON serialization and deserialization of arrays. */
public final class ArrayTypeAdapter<E> extends TypeAdapter<Object> {

  public static final TypeAdapterFactory FACTORY =
      new TypeAdapterFactory() {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
          Type type = typeToken.getType();
          if (!(type instanceof GenericArrayType
              || (type instanceof Class && ((Class<?>) type).isArray()))) {
            return null;
          }

          Type componentType = GsonTypes.getArrayComponentType(type);
          TypeAdapter<?> componentTypeAdapter = gson.getAdapter(TypeToken.get(componentType));
          @SuppressWarnings({"unchecked", "rawtypes"})
          TypeAdapter<T> arrayAdapter =
              new ArrayTypeAdapter(gson, componentTypeAdapter, GsonTypes.getRawType(componentType));
          return arrayAdapter;
        }
      };

  private final Class<E> componentType;
  private final TypeAdapter<E> componentTypeAdapter;

  public ArrayTypeAdapter(
      Gson context, TypeAdapter<E> componentTypeAdapter, Class<E> componentType) {
    this.componentType = componentType;
    this.componentTypeAdapter =
        new TypeAdapterRuntimeTypeWrapper<>(context, componentTypeAdapter, componentType);
  }

  @Override
  public Object read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    List<E> elements = readElements(in);
    return createArrayFromList(elements);
  }

  @Override
  public void write(JsonWriter out, Object array) throws IOException {
    if (array == null) {
      out.nullValue();
      return;
    }

    out.beginArray();
    int length = Array.getLength(array);
    for (int i = 0; i < length; i++) {
      @SuppressWarnings("unchecked")
      E element = (E) Array.get(array, i);
      componentTypeAdapter.write(out, element);
    }
    out.endArray();
  }

  /** Reads JSON array elements and deserializes them into a list of type E. */
  private List<E> readElements(JsonReader in) throws IOException {
    List<E> elements = new ArrayList<>();
    in.beginArray();
    while (in.hasNext()) {
      E element = componentTypeAdapter.read(in);
      elements.add(element);
    }
    in.endArray();
    return elements;
  }

  /** Converts the list of elements into an array of the correct component type. */
  private Object createArrayFromList(List<E> elements) {
    int size = elements.size();

    if (componentType.isPrimitive()) {
      Object array = Array.newInstance(componentType, size);
      for (int i = 0; i < size; i++) {
        Array.set(array, i, elements.get(i));
      }
      return array;
    } else {
      @SuppressWarnings("unchecked")
      E[] array = (E[]) Array.newInstance(componentType, size);
      return elements.toArray(array);
    }
  }
}
