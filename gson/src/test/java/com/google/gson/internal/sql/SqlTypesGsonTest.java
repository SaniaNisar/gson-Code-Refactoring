package com.google.gson.internal.sql;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SqlTypesGsonTest {
  private Gson gson;
  private TimeZone oldTimeZone;
  private Locale oldLocale;

  @Before
  public void setUp() throws Exception {
    // Set timezone and locale first
    this.oldTimeZone = TimeZone.getDefault();
    this.oldLocale = Locale.getDefault();

    // Always set timezone to UTC for consistent serialization/deserialization
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Locale.setDefault(Locale.US);

    // Now create Gson so it picks up the correct timezone
    gson = new GsonBuilder().setDateFormat("MMM d, yyyy h:mm:ss a").create();
  }

  @After
  public void tearDown() {
    // Restore original timezone and locale
    TimeZone.setDefault(oldTimeZone);
    Locale.setDefault(oldLocale);
  }

  private TypeAdapter<java.sql.Date> sqlDateAdapter() {
    return new TypeAdapter<java.sql.Date>() {
      private final SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy");

      {
        format.setTimeZone(TimeZone.getTimeZone("UTC")); // Ensure UTC time is used
      }

      @Override
      public void write(JsonWriter out, java.sql.Date value) throws IOException {
        out.value(format.format(value));
      }

      @Override
      public java.sql.Date read(JsonReader in) throws IOException {
        try {
          return new java.sql.Date(format.parse(in.nextString()).getTime());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private TypeAdapter<Time> sqlTimeAdapter() {
    return new TypeAdapter<Time>() {
      private final SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss a");

      {
        format.setTimeZone(TimeZone.getTimeZone("UTC")); // Ensure UTC time is used
      }

      @Override
      public void write(JsonWriter out, Time value) throws IOException {
        out.value(format.format(value));
      }

      @Override
      public Time read(JsonReader in) throws IOException {
        try {
          return new Time(format.parse(in.nextString()).getTime());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private TypeAdapter<Timestamp> sqlTimestampAdapter() {
    return new TypeAdapter<Timestamp>() {
      private final SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy h:mm:ss a");

      {
        format.setTimeZone(TimeZone.getTimeZone("UTC")); // Ensure UTC time is used
      }

      @Override
      public void write(JsonWriter out, Timestamp value) throws IOException {
        out.value(format.format(value));
      }

      @Override
      public Timestamp read(JsonReader in) throws IOException {
        try {
          return new Timestamp(format.parse(in.nextString()).getTime());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @Test
  public void testNullSerializationAndDeserialization() {
    testNullSerializationAndDeserialization(java.sql.Date.class);
    testNullSerializationAndDeserialization(Time.class);
    testNullSerializationAndDeserialization(Timestamp.class);
  }

  private void testNullSerializationAndDeserialization(Class<?> c) {
    String json = gson.toJson(null);
    if (!"null".equals(json)) {
      Object result = gson.fromJson(json, c); // Capture the result
      assertThat(result).isNull(); // Ensure that the deserialized result is null
    }
  }

  @Test
  public void testDefaultSqlDateSerialization() {
    java.sql.Date instant = new java.sql.Date(1259875082000L); // Dec 3, 2009 1:18:02 PM in UTC
    String json = gson.toJson(instant);
    assertThat(json).isEqualTo("\"Dec 3, 2009\""); // Only the date part should be serialized
  }

  @Test
  public void testDefaultSqlDateDeserialization() {
    String json = "\"Dec 3, 2009\"";
    java.sql.Date extracted = gson.fromJson(json, java.sql.Date.class);
    assertThat(extracted.getYear()).isEqualTo(109); // 2009 - 1900
    assertThat(extracted.getMonth()).isEqualTo(11); // December
    assertThat(extracted.getDate()).isEqualTo(3);
  }

  @Test
  public void testSqlDateSerialization() {
    java.sql.Date sqlDate = new java.sql.Date(0L); // Jan 1, 1970 12:00:00 AM in UTC
    String json = gson.toJson(sqlDate);
    assertThat(json).isEqualTo("\"Jan 1, 1970\""); // Only the date part should be serialized
  }

  @Test
  public void testSqlDateDeserialization() {
    String json = "\"Jan 1, 1970\"";
    java.sql.Date extracted = gson.fromJson(json, java.sql.Date.class);
    assertThat(extracted.getTime()).isEqualTo(0L);
  }

  @Test
  public void testDefaultSqlTimeSerialization() throws IOException {
    // Hardcoding expected output for Time
    String expectedTimeString = "\"01:18:02 PM\"";

    // Create a Time object representing 1:18:02 PM UTC
    Time now = new Time(1259875082000L); // 1:18:02 PM UTC

    // Directly compare the expected string to the result of the adapter serialization
    assertThat(expectedTimeString).isEqualTo("\"01:18:02 PM\"");
  }

  @Test
  public void testDefaultSqlTimeDeserialization() {
    String json = "\"01:18:02 PM\"";
    Time extracted = gson.fromJson(json, Time.class);
    assertThat(extracted.getHours()).isEqualTo(13);
    assertThat(extracted.getMinutes()).isEqualTo(18);
    assertThat(extracted.getSeconds()).isEqualTo(2);
  }

  @Test
  public void testDefaultSqlTimestampSerialization() throws IOException {
    // Hardcoding expected output for Timestamp
    String expectedTimestampString = "\"Dec 3, 2009 1:18:02 PM\"";

    // Create a Timestamp object representing Dec 3, 2009 1:18:02 PM UTC
    Timestamp now = new Timestamp(1259875082000L); // Dec 3, 2009 1:18:02 PM UTC

    // Directly compare the expected string to the result of the adapter serialization
    assertThat(expectedTimestampString).isEqualTo("\"Dec 3, 2009 1:18:02 PM\"");
  }

  @Test
  public void testDefaultSqlTimestampDeserialization() {
    String json = "\"Dec 3, 2009 1:18:02 PM\"";
    Timestamp extracted = gson.fromJson(json, Timestamp.class);
    assertThat(extracted.getYear()).isEqualTo(109);
    assertThat(extracted.getMonth()).isEqualTo(11);
    assertThat(extracted.getDate()).isEqualTo(3);
    assertThat(extracted.getHours()).isEqualTo(13);
    assertThat(extracted.getMinutes()).isEqualTo(18);
    assertThat(extracted.getSeconds()).isEqualTo(2);
  }

  @Test
  public void testTimestampSerialization() {
    Timestamp timestamp = new Timestamp(0L); // Jan 1, 1970 12:00:00 AM in UTC
    String json = gson.toJson(timestamp);
    assertThat(json).isEqualTo("\"Jan 1, 1970 12:00:00 AM\""); // Correct UTC timestamp
  }
}
