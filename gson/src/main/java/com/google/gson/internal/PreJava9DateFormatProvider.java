/*
 * Copyright (C) 2017 The Gson authors
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
package com.google.gson.internal;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Provides DateFormats for US locale with patterns which were the default ones before Java 9. */
public final class PreJava9DateFormatProvider {
  private PreJava9DateFormatProvider() {
    // no instances
  }

  // Single source of truth for date‐style → pattern
  private static final Map<Integer, String> DATE_PATTERNS;
  // Single source of truth for time‐style → pattern
  private static final Map<Integer, String> TIME_PATTERNS;

  static {
    Map<Integer, String> dateMap = new HashMap<>(4);
    dateMap.put(DateFormat.SHORT, "M/d/yy");
    dateMap.put(DateFormat.MEDIUM, "MMM d, yyyy");
    dateMap.put(DateFormat.LONG, "MMMM d, yyyy");
    dateMap.put(DateFormat.FULL, "EEEE, MMMM d, yyyy");
    DATE_PATTERNS = Collections.unmodifiableMap(dateMap);

    Map<Integer, String> timeMap = new HashMap<>(4);
    timeMap.put(DateFormat.SHORT, "h:mm a");
    timeMap.put(DateFormat.MEDIUM, "h:mm:ss a");
    // LONG and FULL share the same pattern
    timeMap.put(DateFormat.LONG, "h:mm:ss a z");
    timeMap.put(DateFormat.FULL, "h:mm:ss a z");
    TIME_PATTERNS = Collections.unmodifiableMap(timeMap);
  }

  /**
   * Returns the same DateFormat as {@code DateFormat.getDateTimeInstance(dateStyle, timeStyle,
   * Locale.US)} did on Java 8 and below.
   *
   * @throws IllegalArgumentException if either style is unrecognized.
   */
  public static DateFormat getUsDateTimeFormat(int dateStyle, int timeStyle) {
    String datePattern = DATE_PATTERNS.get(dateStyle);
    if (datePattern == null) {
      throw new IllegalArgumentException("Unknown DateFormat dateStyle: " + dateStyle);
    }
    String timePattern = TIME_PATTERNS.get(timeStyle);
    if (timePattern == null) {
      throw new IllegalArgumentException("Unknown DateFormat timeStyle: " + timeStyle);
    }
    // Single, well‐formed pattern
    String pattern = datePattern + ' ' + timePattern;
    return new SimpleDateFormat(pattern, Locale.US);
  }
}
