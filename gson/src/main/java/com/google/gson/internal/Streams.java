/*
 * Copyright (C) 2010 Google Inc.
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

package com.google.gson.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import java.io.EOFException;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/** Reads and writes GSON parse trees over streams. */
public final class Streams {
  private Streams() {
    /* no instances */
  }

  /**
   * Takes a reader in any state and returns the next value as a JsonElement.
   *
   * <ul>
   *   <li>If the stream is completely empty (EOF on first peek), returns JsonNull.
   *   <li>If the very first token is END_DOCUMENT, returns JsonNull.
   *   <li>If EOF occurs _after_ we know it’s non‐empty, or any syntax error, throws
   *       JsonSyntaxException.
   *   <li>Any other I/O error becomes a JsonIOException.
   * </ul>
   */
  public static JsonElement parse(JsonReader reader) {
    boolean isEmpty = true;
    try {
      JsonToken token = reader.peek(); // use the result, satisfies CheckReturnValue
      if (token == JsonToken.END_DOCUMENT) {
        // empty or only whitespace → JSON null
        return JsonNull.INSTANCE;
      }
      isEmpty = false;
      return TypeAdapters.JSON_ELEMENT.read(reader);
    } catch (EOFException eof) {
      // EOF on first peek → empty document → JSON null
      if (isEmpty) {
        return JsonNull.INSTANCE;
      }
      // otherwise this is a truncated document
      throw new JsonSyntaxException(eof);
    } catch (MalformedJsonException mf) {
      throw new JsonSyntaxException(mf);
    } catch (IOException io) {
      throw new JsonIOException(io);
    } catch (NumberFormatException nf) {
      throw new JsonSyntaxException(nf);
    }
  }

  /** Writes the JSON element to the writer, recursively. */
  public static void write(JsonElement element, JsonWriter writer) throws IOException {
    TypeAdapters.JSON_ELEMENT.write(writer, element);
  }

  /**
   * Adapts an {@link Appendable} so it can be passed anywhere a {@link Writer} is used. If it’s
   * already a Writer, we just cast; otherwise we wrap it below.
   */
  public static Writer writerForAppendable(Appendable appendable) {
    Objects.requireNonNull(appendable, "appendable == null");
    return (appendable instanceof Writer) ? (Writer) appendable : new AppendableWriter(appendable);
  }

  /**
   * Wraps an {@link Appendable} in a {@link Writer}. Suppress ungrouped‐overloads for our
   * deliberate ordering of overrides.
   */
  @SuppressWarnings("UngroupedOverloads")
  private static final class AppendableWriter extends Writer {
    private final Appendable appendable;
    private final CurrentWrite currentWrite = new CurrentWrite();

    AppendableWriter(Appendable appendable) {
      this.appendable = appendable;
    }

    @Override
    public void write(char[] chars, int offset, int length) throws IOException {
      currentWrite.setChars(chars);
      appendable.append(currentWrite, offset, offset + length);
    }

    @Override
    public void write(int c) throws IOException {
      appendable.append((char) c);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
      Objects.requireNonNull(str, "str == null");
      appendable.append(str, off, off + len);
    }

    @Override
    public void flush() throws IOException {
      /* no-op */
    }

    @Override
    public void close() throws IOException {
      /* no-op */
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
      appendable.append(csq);
      return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
      appendable.append(csq, start, end);
      return this;
    }

    /** A mutable CharSequence over a single char[]. */
    private static class CurrentWrite implements CharSequence {
      private char[] chars;
      private String cachedString;

      void setChars(char[] chars) {
        this.chars = chars;
        this.cachedString = null;
      }

      @Override
      public int length() {
        return chars.length;
      }

      @Override
      public char charAt(int index) {
        return chars[index];
      }

      @Override
      public CharSequence subSequence(int start, int end) {
        return new String(chars, start, end - start);
      }

      @Override
      public String toString() {
        if (cachedString == null) {
          cachedString = new String(chars);
        }
        return cachedString;
      }
    }
  }
}
