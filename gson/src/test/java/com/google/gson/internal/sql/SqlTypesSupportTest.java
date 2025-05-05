package com.google.gson.internal.sql;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class SqlTypesSupportTest {
  @Test
  public void testSupported() {
    // Verify that SQL types are supported
    assertThat(SqlTypesSupport.SUPPORTS_SQL_TYPES).isTrue();

    // Ensure the correct types are initialized
    assertThat(SqlTypesSupport.DATE_DATE_TYPE).isNotNull();
    assertThat(SqlTypesSupport.TIMESTAMP_DATE_TYPE).isNotNull();

    // Ensure the factories are not null
    assertThat(SqlTypesSupport.SQL_DATE_FACTORY).isNotNull(); // Updated to match the corrected name
    assertThat(SqlTypesSupport.SQL_TIME_FACTORY).isNotNull(); // Updated to match the corrected name
    assertThat(SqlTypesSupport.SQL_TIMESTAMP_FACTORY)
        .isNotNull(); // Updated to match the corrected name
  }
}
