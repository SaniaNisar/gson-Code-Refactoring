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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * TypeAdapter for java.sql.Date that handles serialization and deserialization.
 *
 * <p>This adapter maintains thread safety by synchronizing access to the shared DateFormat. Since
 * DateFormat is not thread-safe and captures time zone information when created, special care is
 * taken to preserve the time zone across operations.
 */
@SuppressWarnings("JavaUtilDate")
final class SqlDateTypeAdapter extends TypeAdapter<java.sql.Date> {

  /** Factory for creating SqlDateTypeAdapter instances. */
  static final TypeAdapterFactory FACTORY = new SqlDateTypeAdapterFactory();

  /** Date format pattern used for serialization and deserialization. */
  private static final String DATE_FORMAT_PATTERN = "MMM d, yyyy";

  /** Thread-local DateFormat to avoid synchronization overhead in multi-threaded environments. */
  private static final ThreadLocal<DateFormat> threadLocalFormat =
      ThreadLocal.withInitial(
          () -> {
            SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT_PATTERN);
            format.setTimeZone(TimeZone.getDefault());
            return format;
          });

  /** Private constructor prevents direct instantiation. Use the FACTORY to create instances. */
  private SqlDateTypeAdapter() {}

  @Override
  public java.sql.Date read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    String dateString = in.nextString();
    return parseDate(dateString, in.getPreviousPath());
  }

  /**
   * Parses a date string into a java.sql.Date object.
   *
   * @param dateString The string representation of the date
   * @param jsonPath The JSON path where the date was found (for error reporting)
   * @return A java.sql.Date object
   * @throws JsonSyntaxException If parsing fails
   */
  private java.sql.Date parseDate(String dateString, String jsonPath) throws JsonSyntaxException {
    try {
      DateFormat dateFormat = threadLocalFormat.get();
      Date utilDate = dateFormat.parse(dateString);
      return new java.sql.Date(utilDate.getTime());
    } catch (ParseException e) {
      throw new JsonSyntaxException(
          "Failed parsing '" + dateString + "' as SQL Date; at path " + jsonPath, e);
    }
  }

  @Override
  public void write(JsonWriter out, java.sql.Date value) throws IOException {
    if (value == null) {
      out.nullValue();
      return;
    }

    String dateString = formatDate(value);
    out.value(dateString);
  }

  /**
   * Formats a java.sql.Date object into a string representation.
   *
   * @param value The date to format
   * @return A string representation of the date
   */
  private String formatDate(java.sql.Date value) {
    DateFormat dateFormat = threadLocalFormat.get();
    return dateFormat.format(value);
  }

  /** Factory for creating SqlDateTypeAdapter instances based on the requested type. */
  private static class SqlDateTypeAdapterFactory implements TypeAdapterFactory {
    @SuppressWarnings("unchecked") // Runtime type checking ensures type safety
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
      Class<? super T> rawType = typeToken.getRawType();

      if (rawType == java.sql.Date.class) {
        return (TypeAdapter<T>) new SqlDateTypeAdapter();
      }

      return null;
    }
  }
}
