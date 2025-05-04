package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.PreJava9DateFormatProvider;
import com.google.gson.internal.bind.util.ISO8601Utils;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class DefaultDateTypeAdapter<T extends Date> extends TypeAdapter<T> {
  private static final String SIMPLE_NAME = "DefaultDateTypeAdapter";

  public static final TypeAdapterFactory DEFAULT_STYLE_FACTORY =
      new TypeAdapterFactory() {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
          return typeToken.getRawType() == Date.class
              ? (TypeAdapter<T>)
                  new DefaultDateTypeAdapter<Date>(
                      DateType.DATE, DateFormat.DEFAULT, DateFormat.DEFAULT)
              : null;
        }

        @Override
        public String toString() {
          return "DefaultDateTypeAdapter#DEFAULT_STYLE_FACTORY";
        }
      };

  public abstract static class DateType<T extends Date> {
    public static final DateType<Date> DATE =
        new DateType<Date>(Date.class) {
          @Override
          protected Date deserialize(Date date) {
            return date;
          }
        };

    private final Class<T> dateClass;

    protected DateType(Class<T> dateClass) {
      this.dateClass = dateClass;
    }

    protected abstract T deserialize(Date date);

    private TypeAdapterFactory createFactory(DefaultDateTypeAdapter<T> adapter) {
      return TypeAdapters.newFactory(dateClass, adapter);
    }

    public final TypeAdapterFactory createAdapterFactory(String datePattern) {
      return createFactory(new DefaultDateTypeAdapter<T>(this, datePattern));
    }

    public final TypeAdapterFactory createAdapterFactory(int dateStyle, int timeStyle) {
      return createFactory(new DefaultDateTypeAdapter<T>(this, dateStyle, timeStyle));
    }
  }

  private final DateType<T> dateType;
  private final List<DateFormat> dateFormats = new ArrayList<DateFormat>();

  private DefaultDateTypeAdapter(DateType<T> dateType, String datePattern) {
    if (dateType == null) {
      throw new NullPointerException("dateType == null");
    }
    this.dateType = dateType;
    dateFormats.add(new SimpleDateFormat(datePattern, Locale.US));
    if (!Locale.getDefault().equals(Locale.US)) {
      dateFormats.add(new SimpleDateFormat(datePattern));
    }
  }

  private DefaultDateTypeAdapter(DateType<T> dateType, int dateStyle, int timeStyle) {
    if (dateType == null) {
      throw new NullPointerException("dateType == null");
    }
    this.dateType = dateType;
    dateFormats.add(DateFormat.getDateTimeInstance(dateStyle, timeStyle, Locale.US));
    if (!Locale.getDefault().equals(Locale.US)) {
      dateFormats.add(DateFormat.getDateTimeInstance(dateStyle, timeStyle));
    }

    // Avoid calling JavaVersion.isJava9OrLater() for full Java 8 compatibility
    try {
      DateFormat preJava9 = PreJava9DateFormatProvider.getUsDateTimeFormat(dateStyle, timeStyle);
      if (preJava9 != null) {
        dateFormats.add(preJava9);
      }
    } catch (Exception e) {
      // Ignore: means fallback format not available, still works
    }
  }

  @Override
  public void write(JsonWriter out, Date value) throws IOException {
    if (value == null) {
      out.nullValue();
      return;
    }

    DateFormat dateFormat = dateFormats.get(0);
    String dateFormatAsString;
    synchronized (dateFormats) {
      dateFormatAsString = dateFormat.format(value);
    }
    out.value(dateFormatAsString);
  }

  @Override
  public T read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    Date date = deserializeToDate(in);
    return dateType.deserialize(date);
  }

  private Date deserializeToDate(JsonReader in) throws IOException {
    String s = in.nextString();
    synchronized (dateFormats) {
      for (DateFormat dateFormat : dateFormats) {
        TimeZone originalTimeZone = dateFormat.getTimeZone();
        try {
          return dateFormat.parse(s);
        } catch (ParseException ignored) {
        } finally {
          dateFormat.setTimeZone(originalTimeZone);
        }
      }
    }

    try {
      return ISO8601Utils.parse(s, new ParsePosition(0));
    } catch (ParseException e) {
      throw new JsonSyntaxException(
          "Failed parsing '" + s + "' as Date; at path " + in.getPreviousPath(), e);
    }
  }

  @Override
  public String toString() {
    DateFormat defaultFormat = dateFormats.get(0);
    if (defaultFormat instanceof SimpleDateFormat) {
      return SIMPLE_NAME + '(' + ((SimpleDateFormat) defaultFormat).toPattern() + ')';
    } else {
      return SIMPLE_NAME + '(' + defaultFormat.getClass().getSimpleName() + ')';
    }
  }
}
