/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal.sqlcommenter;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterBuilder;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqlCommenterCustomizerHolder {
  private static volatile SqlCommenterCustomizer customizer =
      new SqlCommenterCustomizerHolder.NoOpCustomizer();

  public static void setCustomizer(SqlCommenterCustomizer customizer) {
    SqlCommenterCustomizerHolder.customizer = customizer;
  }

  public static SqlCommenterCustomizer getCustomizer() {
    return customizer;
  }

  private SqlCommenterCustomizerHolder() {}

  private static class NoOpCustomizer implements SqlCommenterCustomizer {

    @Override
    public void customize(SqlCommenterBuilder builder) {}
  }
}
