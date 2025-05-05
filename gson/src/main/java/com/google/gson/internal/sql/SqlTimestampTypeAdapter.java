package com.google.gson.internal.sql;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public final class SqlTimestampTypeAdapter extends BaseSqlDateAdapter<Timestamp> {
  private static final String DATE_FORMAT = "MMM d, yyyy h:mm:ss a";

  // Constructor initializes the base adapter with the timestamp format
  public SqlTimestampTypeAdapter() {
    super(new SimpleDateFormat(DATE_FORMAT));
  }

  // Override the read method to parse a timestamp from JSON
  @Override
  public Timestamp read(JsonReader in) throws IOException {
    if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    return new Timestamp(parseDate(in, "Timestamp").getTime());
  }
}
