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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

/**
 * This reader walks the elements of a JsonElement as if it was coming from a character stream.
 *
 * @author Jesse Wilson
 */
public final class JsonTreeReader extends JsonReader {
  private static final Reader UNREADABLE_READER = new UnreadableReader();
  private static final Object SENTINEL_CLOSED = new Object();
  private static final int INITIAL_STACK_SIZE = 32;

  private final JsonStack stack;

  public JsonTreeReader(JsonElement element) {
    super(UNREADABLE_READER);
    this.stack = new JsonStack(INITIAL_STACK_SIZE);
    stack.push(element);
  }

  @Override
  public void beginArray() throws IOException {
    expect(JsonToken.BEGIN_ARRAY);
    JsonArray array = (JsonArray) stack.peek();
    stack.push(array.iterator());
    stack.setPathIndex(0);
  }

  @Override
  public void endArray() throws IOException {
    expect(JsonToken.END_ARRAY);
    stack.pop(); // empty iterator
    stack.pop(); // array
    stack.incrementPathIndex();
  }

  @Override
  public void beginObject() throws IOException {
    expect(JsonToken.BEGIN_OBJECT);
    JsonObject object = (JsonObject) stack.peek();
    stack.push(object.entrySet().iterator());
  }

  @Override
  public void endObject() throws IOException {
    expect(JsonToken.END_OBJECT);
    stack.setPathName(null); // Free the last path name so that it can be garbage collected
    stack.pop(); // empty iterator
    stack.pop(); // object
    stack.incrementPathIndex();
  }

  @Override
  public boolean hasNext() throws IOException {
    JsonToken token = peek();
    return token != JsonToken.END_OBJECT
        && token != JsonToken.END_ARRAY
        && token != JsonToken.END_DOCUMENT;
  }

  @Override
  public JsonToken peek() throws IOException {
    if (stack.isEmpty()) {
      return JsonToken.END_DOCUMENT;
    }

    Object current = stack.peek();
    return determineTokenType(current);
  }

  private JsonToken determineTokenType(Object obj) throws IOException {
    if (obj instanceof Iterator) {
      return handleIterator((Iterator<?>) obj);
    } else if (obj instanceof JsonObject) {
      return JsonToken.BEGIN_OBJECT;
    } else if (obj instanceof JsonArray) {
      return JsonToken.BEGIN_ARRAY;
    } else if (obj instanceof JsonPrimitive) {
      return determineJsonPrimitiveType((JsonPrimitive) obj);
    } else if (obj instanceof JsonNull) {
      return JsonToken.NULL;
    } else if (obj == SENTINEL_CLOSED) {
      throw new IllegalStateException("JsonReader is closed");
    } else {
      throw new MalformedJsonException(
          "Custom JsonElement subclass " + obj.getClass().getName() + " is not supported");
    }
  }

  private JsonToken handleIterator(Iterator<?> iterator) throws IOException {
    boolean isObject = stack.isSecondFromTopInstanceOf(JsonObject.class);
    if (iterator.hasNext()) {
      if (isObject) {
        return JsonToken.NAME;
      } else {
        stack.push(iterator.next());
        return peek();
      }
    } else {
      return isObject ? JsonToken.END_OBJECT : JsonToken.END_ARRAY;
    }
  }

  private JsonToken determineJsonPrimitiveType(JsonPrimitive primitive) {
    if (primitive.isString()) {
      return JsonToken.STRING;
    } else if (primitive.isBoolean()) {
      return JsonToken.BOOLEAN;
    } else if (primitive.isNumber()) {
      return JsonToken.NUMBER;
    } else {
      throw new AssertionError();
    }
  }

  private void expect(JsonToken expected) throws IOException {
    JsonToken actual = peek();
    if (actual != expected) {
      throw new IllegalStateException(
          "Expected " + expected + " but was " + actual + locationString());
    }
  }

  private String nextName(boolean skipName) throws IOException {
    expect(JsonToken.NAME);
    Iterator<?> i = (Iterator<?>) stack.peek();
    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) i.next();
    String result = (String) entry.getKey();
    stack.setPathName(skipName ? "<skipped>" : result);
    stack.push(entry.getValue());
    return result;
  }

  @Override
  public String nextName() throws IOException {
    return nextName(false);
  }

  @Override
  public String nextString() throws IOException {
    JsonToken token = peek();
    if (token != JsonToken.STRING && token != JsonToken.NUMBER) {
      throw new IllegalStateException(
          "Expected " + JsonToken.STRING + " but was " + token + locationString());
    }
    String result = ((JsonPrimitive) stack.pop()).getAsString();
    stack.incrementPathIndex();
    return result;
  }

  @Override
  public boolean nextBoolean() throws IOException {
    expect(JsonToken.BOOLEAN);
    boolean result = ((JsonPrimitive) stack.pop()).getAsBoolean();
    stack.incrementPathIndex();
    return result;
  }

  @Override
  public void nextNull() throws IOException {
    expect(JsonToken.NULL);
    stack.pop();
    stack.incrementPathIndex();
  }

  @Override
  public double nextDouble() throws IOException {
    return nextNumeric(JsonValueExtractor.DOUBLE);
  }

  @Override
  public long nextLong() throws IOException {
    return nextNumeric(JsonValueExtractor.LONG);
  }

  @Override
  public int nextInt() throws IOException {
    return nextNumeric(JsonValueExtractor.INT);
  }

  private <T> T nextNumeric(JsonValueExtractor<T> extractor) throws IOException {
    JsonToken token = peek();
    if (token != JsonToken.NUMBER && token != JsonToken.STRING) {
      throw new IllegalStateException(
          "Expected " + JsonToken.NUMBER + " but was " + token + locationString());
    }

    JsonPrimitive primitive = (JsonPrimitive) stack.peek();
    T result = extractor.extract(primitive);

    if (extractor == JsonValueExtractor.DOUBLE && !isLenient()) {
      double doubleValue = (Double) result;
      if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
        throw new MalformedJsonException("JSON forbids NaN and infinities: " + doubleValue);
      }
    }

    stack.pop();
    stack.incrementPathIndex();
    return result;
  }

  JsonElement nextJsonElement() throws IOException {
    JsonToken peeked = peek();
    if (peeked == JsonToken.NAME
        || peeked == JsonToken.END_ARRAY
        || peeked == JsonToken.END_OBJECT
        || peeked == JsonToken.END_DOCUMENT) {
      throw new IllegalStateException("Unexpected " + peeked + " when reading a JsonElement.");
    }
    JsonElement element = (JsonElement) stack.peek();
    skipValue();
    return element;
  }

  @Override
  public void close() throws IOException {
    stack.clear();
    stack.push(SENTINEL_CLOSED);
  }

  @Override
  public void skipValue() throws IOException {
    JsonToken peeked = peek();
    switch (peeked) {
      case NAME:
        nextName(true);
        break;
      case END_ARRAY:
        endArray();
        break;
      case END_OBJECT:
        endObject();
        break;
      case END_DOCUMENT:
        // Do nothing
        break;
      default:
        stack.pop();
        stack.incrementPathIndex();
        break;
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + locationString();
  }

  public void promoteNameToValue() throws IOException {
    expect(JsonToken.NAME);
    Iterator<?> i = (Iterator<?>) stack.peek();
    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) i.next();
    stack.push(entry.getValue());
    stack.push(new JsonPrimitive((String) entry.getKey()));
  }

  @Override
  public String getPath() {
    return stack.getPath(false);
  }

  @Override
  public String getPreviousPath() {
    return stack.getPath(true);
  }

  private String locationString() {
    return " at path " + getPath();
  }

  /** Represents different ways to extract values from JsonPrimitive. */
  private interface JsonValueExtractor<T> {
    T extract(JsonPrimitive primitive);

    JsonValueExtractor<Double> DOUBLE = JsonPrimitive::getAsDouble;
    JsonValueExtractor<Long> LONG = JsonPrimitive::getAsLong;
    JsonValueExtractor<Integer> INT = JsonPrimitive::getAsInt;
  }

  /** Encapsulates the stack and path tracking logic. */
  private static class JsonStack {
    private Object[] stack;
    private int size;
    private String[] pathNames;
    private int[] pathIndices;

    JsonStack(int initialCapacity) {
      this.stack = new Object[initialCapacity];
      this.pathNames = new String[initialCapacity];
      this.pathIndices = new int[initialCapacity];
      this.size = 0;
    }

    boolean isEmpty() {
      return size == 0;
    }

    void push(Object value) {
      ensureCapacity();
      stack[size++] = value;
    }

    @CanIgnoreReturnValue
    Object pop() {
      Object result = stack[--size];
      stack[size] = null; // Help GC
      return result;
    }

    Object peek() {
      return size > 0 ? stack[size - 1] : null;
    }

    void clear() {
      size = 0;
      for (int i = 0; i < stack.length; i++) {
        stack[i] = null;
        pathNames[i] = null;
      }
    }

    boolean isSecondFromTopInstanceOf(Class<?> clazz) {
      return size >= 2 && clazz.isInstance(stack[size - 2]);
    }

    void setPathIndex(int index) {
      if (size > 0) {
        pathIndices[size - 1] = index;
      }
    }

    void incrementPathIndex() {
      if (size > 0) {
        pathIndices[size - 1]++;
      }
    }

    void setPathName(String name) {
      if (size > 0) {
        pathNames[size - 1] = name;
      }
    }

    private void ensureCapacity() {
      if (size == stack.length) {
        int newLength = size * 2;
        stack = java.util.Arrays.copyOf(stack, newLength);
        pathIndices = java.util.Arrays.copyOf(pathIndices, newLength);
        pathNames = java.util.Arrays.copyOf(pathNames, newLength);
      }
    }

    String getPath(boolean usePreviousPath) {
      StringBuilder result = new StringBuilder().append('$');
      buildPath(result, usePreviousPath);
      return result.toString();
    }

    private void buildPath(StringBuilder result, boolean usePreviousPath) {
      for (int i = 0; i < size; i++) {
        if (stack[i] instanceof JsonArray) {
          if (++i < size && stack[i] instanceof Iterator) {
            appendArrayPath(result, i, usePreviousPath);
          }
        } else if (stack[i] instanceof JsonObject) {
          if (++i < size && stack[i] instanceof Iterator) {
            appendObjectPath(result, i);
          }
        }
      }
    }

    private void appendArrayPath(StringBuilder result, int stackIndex, boolean usePreviousPath) {
      int pathIndex = pathIndices[stackIndex];
      // If index is last path element it points to next array element; have to decrement
      // `- 1` covers case where iterator for next element is on stack
      // `- 2` covers case where peek() already pushed next element onto stack
      if (usePreviousPath && pathIndex > 0 && (stackIndex == size - 1 || stackIndex == size - 2)) {
        pathIndex--;
      }
      result.append('[').append(pathIndex).append(']');
    }

    private void appendObjectPath(StringBuilder result, int stackIndex) {
      result.append('.');
      if (pathNames[stackIndex] != null) {
        result.append(pathNames[stackIndex]);
      }
    }
  }

  /** A Reader that throws AssertionError when used. */
  private static class UnreadableReader extends Reader {
    @Override
    public int read(char[] buffer, int offset, int count) {
      throw new AssertionError();
    }

    @Override
    public void close() {
      throw new AssertionError();
    }
  }
}
