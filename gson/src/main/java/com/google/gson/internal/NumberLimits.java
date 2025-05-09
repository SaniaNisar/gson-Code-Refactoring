package com.google.gson.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * This class enforces limits on numbers parsed from JSON to avoid potential performance problems
 * when extremely large numbers are used.
 */
public final class NumberLimits {
  private NumberLimits() {
    // no instances
  }

  // ----- constants -----
  private static final int MAX_NUMBER_STRING_LENGTH = 10_000;
  private static final int MAX_SCALE = 10_000;
  private static final int PREVIEW_LENGTH = 30;

  // ----- helpers -----

  /**
   * If the string is longer than PREVIEW_LENGTH, returns the first PREVIEW_LENGTH chars + "...".
   */
  private static String preview(String s) {
    return (s.length() <= PREVIEW_LENGTH) ? s : s.substring(0, PREVIEW_LENGTH) + "...";
  }

  /**
   * Throws NumberFormatException with the **exact** message the tests expect if the input length is
   * too long.
   */
  private static void checkLength(String s) {
    if (s.length() > MAX_NUMBER_STRING_LENGTH) {
      // **must** use this exact wording:
      throw new NumberFormatException("Number string too large: " + preview(s));
    }
  }

  /**
   * Parses a BigDecimal, enforcing the same string‐length limit and the original scale‐limit check.
   * The test suite inspects the cause's message on failure.
   *
   * @throws NullPointerException if s is null
   * @throws NumberFormatException on too‐long string or unsupported scale
   */
  public static BigDecimal parseBigDecimal(String s) {
    Objects.requireNonNull(s, "Number string cannot be null");
    checkLength(s);

    BigDecimal decimal = new BigDecimal(s);

    // original scale check from upstream
    if (Math.abs((long) decimal.scale()) >= MAX_SCALE) {
      throw new NumberFormatException("Number has unsupported scale: " + s);
    }
    return decimal;
  }

  /**
   * Parses a BigInteger, enforcing only the string‐length limit.
   *
   * @throws NullPointerException if s is null
   * @throws NumberFormatException on too‐long string
   */
  public static BigInteger parseBigInteger(String s) {
    Objects.requireNonNull(s, "Number string cannot be null");
    checkLength(s);
    return new BigInteger(s);
  }
}
