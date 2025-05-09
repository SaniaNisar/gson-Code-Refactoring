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

import java.util.regex.Pattern;

/** Utility to check the major Java version of the current JVM. */
public final class JavaVersion {
  /** Minimum supported JDK version when parsing fails. */
  private static final int DEFAULT_MAJOR_VERSION = 6;

  /** Regex `[._]` used to split version strings like "1.8.0_202" or "9.0.4". */
  private static final Pattern VERSION_DELIMITER = Pattern.compile("[._]");

  /** Cached at class‐load time to avoid repeated parsing. */
  private static final int majorJavaVersion = determineMajorJavaVersion();

  private JavaVersion() {
    // no instances
  }

  /** Read the system property once, guard null/empty, and parse. */
  private static int determineMajorJavaVersion() {
    String version = System.getProperty("java.version");
    if (version == null || version.isEmpty()) {
      return DEFAULT_MAJOR_VERSION;
    }
    return parseMajorJavaVersion(version);
  }

  /**
   * Visible for testing. Parses the major version from strings like "1.8.0_202", "9.0.4", or
   * "11-ea".
   */
  static int parseMajorJavaVersion(String version) {
    int v = parseDotted(version);
    if (v < 0) {
      v = extractLeadingInt(version);
    }
    return v < 0 ? DEFAULT_MAJOR_VERSION : v;
  }

  /** Try splitting on `.` or `_` and parsing the first one or two components. */
  private static int parseDotted(String version) {
    try {
      String[] parts = VERSION_DELIMITER.split(version, 3);
      int first = Integer.parseInt(parts[0]);
      // Legacy "1.x" → return x; otherwise return first
      if (first == 1 && parts.length > 1) {
        return Integer.parseInt(parts[1]);
      }
      return first;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** Extracts a leading integer (e.g. "11-ea" ⇒ 11). */
  private static int extractLeadingInt(String version) {
    int i = 0;
    int len = version.length();
    while (i < len && Character.isDigit(version.charAt(i))) {
      i++;
    }
    if (i == 0) {
      return -1;
    }
    try {
      return Integer.parseInt(version.substring(0, i));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** The major Java version: 8 for 1.8, 9 for Java 9, etc. */
  public static int getMajorJavaVersion() {
    return majorJavaVersion;
  }

  /** Returns true if running on Java 9 or later. */
  public static boolean isJava9OrLater() {
    return majorJavaVersion >= 9;
  }
}
