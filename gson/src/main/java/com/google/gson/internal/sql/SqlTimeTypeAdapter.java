/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.gson.internal.sql;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Adapter for java.sql.Time. Handles serialization and deserialization of SQL Time objects. */
@SuppressWarnings("JavaUtilDate")
final class SqlTimeTypeAdapter extends TypeAdapter<Time> {

  /** The format pattern used for time formatting. */
  private static final String TIME_FORMAT_PATTERN = "hh:mm:ss a";

  /** Factory for creating SqlTimeTypeAdapter instances. */
  static final TypeAdapterFactory FACTORY = new SqlTimeTypeAdapterFactory();

  /** Constructs a new SqlTimeTypeAdapter. */
  SqlTimeTypeAdapter() {
    // Default constructor
  }

  /**
   * Creates a thread-local DateFormat instance.
   *
   * @return A new DateFormat instance with the time pattern
   */
  private static DateFormat createFormat() {
    return new SimpleDateFormat(TIME_FORMAT_PATTERN);
  }

  @Override
  public Time read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    String timeString = in.nextString();
    try {
      // Create a new DateFormat for each parse operation to avoid thread-safety issues
      DateFormat format = createFormat();
      Date date = format.parse(timeString);
      return new Time(date.getTime());
    } catch (ParseException e) {
      throw new JsonSyntaxException(
          "Failed parsing '" + timeString + "' as SQL Time; at path " + in.getPreviousPath(), e);
    }
  }

  @Override
  public void write(JsonWriter out, Time value) throws IOException {
    if (value == null) {
      out.nullValue();
      return;
    }

    // Create a new DateFormat for each format operation to avoid thread-safety issues
    DateFormat format = createFormat();
    String timeString = format.format(value);
    out.value(timeString);
  }

  /** Factory implementation for SqlTimeTypeAdapter. */
  private static class SqlTimeTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
      Class<? super T> rawType = typeToken.getRawType();
      if (!Time.class.isAssignableFrom(rawType)) {
        return null;
      }

      @SuppressWarnings("unchecked") // Type safety ensured by isAssignableFrom check
      TypeAdapter<T> adapter = (TypeAdapter<T>) new SqlTimeTypeAdapter();
      return adapter;
    }
  }
}
