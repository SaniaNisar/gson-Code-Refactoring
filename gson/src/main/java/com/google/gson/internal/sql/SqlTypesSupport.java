/*
 * Copyright (C) 2020 Google Inc.
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

import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.DefaultDateTypeAdapter.DateType;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Encapsulates access to {@code java.sql} types, to allow Gson to work without the {@code java.sql}
 * module being present. No {@link ClassNotFoundException}s will be thrown in case the {@code
 * java.sql} module is not present.
 *
 * <p>If SQL types are supported, all constants of this class will be non-{@code null}. However, if
 * not supported, all other constants will be {@code null}.
 */
@SuppressWarnings("JavaUtilDate")
public final class SqlTypesSupport {

  private static final String SQL_DATE_CLASS_NAME = "java.sql.Date";

  /** {@code true} if {@code java.sql} types are supported, {@code false} otherwise */
  public static final boolean SUPPORTS_SQL_TYPES = checkSqlTypesSupport();

  public static final DateType<? extends Date> DATE_DATE_TYPE = createSqlDateType();
  public static final DateType<? extends Date> TIMESTAMP_DATE_TYPE = createTimestampDateType();

  public static final TypeAdapterFactory DATE_FACTORY =
      SUPPORTS_SQL_TYPES ? SqlDateTypeAdapter.FACTORY : null;
  public static final TypeAdapterFactory TIME_FACTORY =
      SUPPORTS_SQL_TYPES ? SqlTimeTypeAdapter.FACTORY : null;
  public static final TypeAdapterFactory TIMESTAMP_FACTORY =
      SUPPORTS_SQL_TYPES ? SqlTimestampTypeAdapter.FACTORY : null;

  /**
   * Checks if SQL types are supported by attempting to load a SQL class.
   *
   * @return true if SQL types are supported, false otherwise
   */
  private static boolean checkSqlTypesSupport() {
    try {
      Class.forName(SQL_DATE_CLASS_NAME);
      return true;
    } catch (ClassNotFoundException classNotFoundException) {
      return false;
    }
  }

  /**
   * Creates the SQL Date type if supported.
   *
   * @return DateType for SQL Date or null if not supported
   */
  private static DateType<? extends Date> createSqlDateType() {
    if (!SUPPORTS_SQL_TYPES) {
      return null;
    }

    return new DateType<java.sql.Date>(java.sql.Date.class) {
      @Override
      protected java.sql.Date deserialize(Date date) {
        return new java.sql.Date(date.getTime());
      }
    };
  }

  /**
   * Creates the SQL Timestamp type if supported.
   *
   * @return DateType for SQL Timestamp or null if not supported
   */
  private static DateType<? extends Date> createTimestampDateType() {
    if (!SUPPORTS_SQL_TYPES) {
      return null;
    }

    return new DateType<Timestamp>(Timestamp.class) {
      @Override
      protected Timestamp deserialize(Date date) {
        return new Timestamp(date.getTime());
      }
    };
  }

  // Prevent instantiation
  private SqlTypesSupport() {}
}
