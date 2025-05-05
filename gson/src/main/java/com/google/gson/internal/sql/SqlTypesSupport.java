package com.google.gson.internal.sql;

import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.TypeAdapters;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

public final class SqlTypesSupport {
  // TypeAdapterFactories for SQL Date, Time, and Timestamp
  public static final TypeAdapterFactory SQL_DATE_FACTORY =
      TypeAdapters.newFactory(Date.class, new SqlDateTypeAdapter());
  public static final TypeAdapterFactory SQL_TIME_FACTORY =
      TypeAdapters.newFactory(Time.class, new SqlTimeTypeAdapter());
  public static final TypeAdapterFactory SQL_TIMESTAMP_FACTORY =
      TypeAdapters.newFactory(Timestamp.class, new SqlTimestampTypeAdapter());

  // Add these constants here for DATE and TIMESTAMP types
  public static final String DATE_DATE_TYPE = "DATE";
  public static final String TIMESTAMP_DATE_TYPE = "TIMESTAMP";
  public static final String TIME_DATE_TYPE = "TIME";

  // Method to create adapter factory for each type
  public static TypeAdapterFactory createAdapterFactory(String type) {
    switch (type) {
      case DATE_DATE_TYPE:
        return SQL_DATE_FACTORY;
      case TIMESTAMP_DATE_TYPE:
        return SQL_TIMESTAMP_FACTORY;
      case TIME_DATE_TYPE:
        return SQL_TIME_FACTORY;
      default:
        throw new IllegalArgumentException("Unsupported SQL type: " + type);
    }
  }

  // Constants to use in the check for SQL Types
  public static final boolean SUPPORTS_SQL_TYPES = true; // or false depending on your requirements

  private SqlTypesSupport() {
    // Private constructor to prevent instantiation
  }
}
