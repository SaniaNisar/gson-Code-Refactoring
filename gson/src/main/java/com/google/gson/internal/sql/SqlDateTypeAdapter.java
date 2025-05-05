package com.google.gson.internal.sql;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;

public final class SqlDateTypeAdapter extends BaseSqlDateAdapter<Date> {
  private static final String DATE_FORMAT = "MMM d, yyyy";

  // Constructor initializes the base adapter with the date format
  public SqlDateTypeAdapter() {
    super(new SimpleDateFormat(DATE_FORMAT));
  }

  // Override the read method to parse a date from JSON
  @Override
  public Date read(JsonReader in) throws IOException {
    if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    return new Date(parseDate(in, "Date").getTime());
  }
}
