/*
 * Copyright (C) 2011 Google Inc.
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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Holds a JSON number literal that is only parsed into a concrete {@link Number} type on demand.
 */
@SuppressWarnings("serial") // Number is Serializable
public final class LazilyParsedNumber extends Number {
  private final String value;
  // Cached BigDecimal, initialized on first access
  private transient volatile BigDecimal decimalCache;

  /**
   * @param value must not be null.
   */
  public LazilyParsedNumber(String value) {
    this.value = Objects.requireNonNull(value, "value == null");
  }

  /** Lazily parse and cache a BigDecimal of the original string. */
  private BigDecimal getDecimal() {
    BigDecimal result = decimalCache;
    if (result == null) {
      result = NumberLimits.parseBigDecimal(value);
      decimalCache = result;
    }
    return result;
  }

  @Override
  public int intValue() {
    // Narrow from long, consistent overflow handling
    return (int) longValue();
  }

  @Override
  public long longValue() {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return getDecimal().longValue();
    }
  }

  @Override
  public float floatValue() {
    return Float.parseFloat(value);
  }

  @Override
  public double doubleValue() {
    return Double.parseDouble(value);
  }

  @Override
  public String toString() {
    return value;
  }

  /**
   * When serializing, replace this instance with a {@link BigDecimal} so the receiver needn't know
   * about Gson’s internal class.
   */
  @SuppressWarnings("unused")
  private Object writeReplace() throws ObjectStreamException {
    return getDecimal();
  }

  /**
   * Prevent deserialization of this internal class; {@code writeReplace()} should have replaced it.
   */
  @SuppressWarnings("unused")
  private void readObject(ObjectInputStream in) throws IOException {
    throw new InvalidObjectException("Deserialization is unsupported");
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return (obj == this)
        || (obj instanceof LazilyParsedNumber && value.equals(((LazilyParsedNumber) obj).value));
  }
}
