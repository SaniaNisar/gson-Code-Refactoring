package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.ToNumberPolicy;
import com.google.gson.ToNumberStrategy;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/** Type adapter for {@link Number}. */
public final class NumberTypeAdapter extends TypeAdapter<Number> {

  // Singleton instances of the adapter for each ToNumberStrategy
  private static final TypeAdapterFactory LAZILY_PARSED_NUMBER_FACTORY =
      createFactory(ToNumberPolicy.LAZILY_PARSED_NUMBER);

  private final ToNumberStrategy toNumberStrategy;

  private NumberTypeAdapter(ToNumberStrategy toNumberStrategy) {
    this.toNumberStrategy = toNumberStrategy;
  }

  // Create a TypeAdapterFactory based on the provided strategy
  private static TypeAdapterFactory createFactory(ToNumberStrategy strategy) {
    return new TypeAdapterFactory() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        return type.getRawType() == Number.class
            ? (TypeAdapter<T>) new NumberTypeAdapter(strategy)
            : null;
      }
    };
  }

  // Factory accessor method
  public static TypeAdapterFactory getFactory(ToNumberStrategy toNumberStrategy) {
    return toNumberStrategy == ToNumberPolicy.LAZILY_PARSED_NUMBER
        ? LAZILY_PARSED_NUMBER_FACTORY
        : createFactory(toNumberStrategy);
  }

  @Override
  public Number read(JsonReader in) throws IOException {
    JsonToken jsonToken = in.peek();
    if (jsonToken == JsonToken.NULL) {
      in.nextNull();
      return null;
    } else if (jsonToken == JsonToken.NUMBER || jsonToken == JsonToken.STRING) {
      return toNumberStrategy.readNumber(in);
    } else {
      throw new JsonSyntaxException(
          "Expecting number, got: " + jsonToken + "; at path " + in.getPath());
    }
  }

  @Override
  public void write(JsonWriter out, Number value) throws IOException {
    out.value(value);
  }
}
