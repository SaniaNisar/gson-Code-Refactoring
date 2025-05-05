package com.google.gson.internal.sql;

import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;

public abstract class BaseSqlDateAdapter<T extends Date> extends TypeAdapter<T> {
  protected final ThreadLocal<DateFormat> threadLocalFormat;

  // Constructor accepts a DateFormat object
  protected BaseSqlDateAdapter(DateFormat format) {
    this.threadLocalFormat =
        ThreadLocal.withInitial(
            () -> {
              // Set the format to use the system default timezone instead of UTC
              format.setTimeZone(TimeZone.getDefault()); // Default system timezone
              return format;
            });
  }

  @Override
  public void write(JsonWriter out, T value) throws IOException {
    if (value == null) {
      out.nullValue();
    } else {
      out.value(threadLocalFormat.get().format(value));
    }
  }

  // Protected method to parse a date from JsonReader
  protected Date parseDate(JsonReader in, String type) throws IOException {
    String dateString = in.nextString();
    try {
      return threadLocalFormat.get().parse(dateString);
    } catch (ParseException e) {
      throw new JsonSyntaxException(
          String.format("Error parsing SQL %s '%s' at path %s", type, dateString, in.getPath()), e);
    }
  }
}
