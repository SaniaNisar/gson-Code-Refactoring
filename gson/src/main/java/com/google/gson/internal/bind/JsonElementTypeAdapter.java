package com.google.gson.internal.bind;

import com.google.gson.*;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/** Adapter for {@link JsonElement} and subclasses. */
class JsonElementTypeAdapter extends TypeAdapter<JsonElement> {
  static final JsonElementTypeAdapter ADAPTER = new JsonElementTypeAdapter();

  private JsonElementTypeAdapter() {}

  private JsonElement tryBeginNesting(JsonReader in, JsonToken token) throws IOException {
    switch (token) {
      case BEGIN_ARRAY:
        in.beginArray();
        return new JsonArray();
      case BEGIN_OBJECT:
        in.beginObject();
        return new JsonObject();
      default:
        return null;
    }
  }

  private JsonElement readTerminal(JsonReader in, JsonToken token) throws IOException {
    switch (token) {
      case STRING:
        return new JsonPrimitive(in.nextString());
      case NUMBER:
        return new JsonPrimitive(new LazilyParsedNumber(in.nextString()));
      case BOOLEAN:
        return new JsonPrimitive(in.nextBoolean());
      case NULL:
        in.nextNull();
        return JsonNull.INSTANCE;
      default:
        throw new IllegalStateException("Unexpected token: " + token);
    }
  }

  @Override
  public JsonElement read(JsonReader in) throws IOException {
    if (in instanceof JsonTreeReader) {
      return ((JsonTreeReader) in).nextJsonElement();
    }

    JsonToken token = in.peek();
    JsonElement root = tryBeginNesting(in, token);
    if (root == null) {
      return readTerminal(in, token);
    }

    Deque<JsonElement> stack = new ArrayDeque<>();

    JsonElement current = root;

    while (true) {
      while (in.hasNext()) {
        String name = (current instanceof JsonObject) ? in.nextName() : null;
        token = in.peek();
        JsonElement value = tryBeginNesting(in, token);

        if (value == null) {
          value = readTerminal(in, token);
        }

        if (current instanceof JsonArray) {
          ((JsonArray) current).add(value);
        } else {
          ((JsonObject) current).add(name, value);
        }

        if (value instanceof JsonArray || value instanceof JsonObject) {
          stack.addLast(current);
          current = value;
        }
      }

      if (current instanceof JsonArray) {
        in.endArray();
      } else {
        in.endObject();
      }

      if (stack.isEmpty()) {
        return current;
      }

      current = stack.removeLast();
    }
  }

  @Override
  public void write(JsonWriter out, JsonElement value) throws IOException {
    if (value == null || value.isJsonNull()) {
      out.nullValue();
      return;
    }

    if (value.isJsonPrimitive()) {
      writePrimitive(out, value.getAsJsonPrimitive());
    } else if (value.isJsonArray()) {
      writeArray(out, value.getAsJsonArray());
    } else if (value.isJsonObject()) {
      writeObject(out, value.getAsJsonObject());
    } else {
      throw new IllegalArgumentException("Couldn't write " + value.getClass());
    }
  }

  private void writePrimitive(JsonWriter out, JsonPrimitive primitive) throws IOException {
    if (primitive.isNumber()) {
      out.value(primitive.getAsNumber());
    } else if (primitive.isBoolean()) {
      out.value(primitive.getAsBoolean());
    } else {
      out.value(primitive.getAsString());
    }
  }

  private void writeArray(JsonWriter out, JsonArray array) throws IOException {
    out.beginArray();
    for (JsonElement element : array) {
      write(out, element);
    }
    out.endArray();
  }

  private void writeObject(JsonWriter out, JsonObject object) throws IOException {
    out.beginObject();
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      out.name(entry.getKey());
      write(out, entry.getValue());
    }
    out.endObject();
  }
}
