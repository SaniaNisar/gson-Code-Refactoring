package com.google.gson.internal.sql;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.sql.Time;
import java.text.SimpleDateFormat;

public final class SqlTimeTypeAdapter extends BaseSqlDateAdapter<Time> {
  private static final String TIME_FORMAT = "hh:mm:ss a";

  // Constructor initializes the base adapter with the time format
  public SqlTimeTypeAdapter() {
    super(new SimpleDateFormat(TIME_FORMAT));
  }

  // Override the read method to parse a time from JSON
  @Override
  public Time read(JsonReader in) throws IOException {
    if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    return new Time(parseDate(in, "Time").getTime());
  }
}
