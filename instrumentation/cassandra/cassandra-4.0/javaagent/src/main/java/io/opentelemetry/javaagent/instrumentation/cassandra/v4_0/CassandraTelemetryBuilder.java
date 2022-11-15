/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;

public class CassandraTelemetryBuilder {

  static final String DEFAULT_INSTRUMENTATION_NAME = "io.opentelemetry.cassandra";

  private final OpenTelemetry openTelemetry;

  private String instrumentationName = DEFAULT_INSTRUMENTATION_NAME;
  private boolean statementSanitizationEnabled;

  public CassandraTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public CassandraTelemetryBuilder setInstrumentationName(String instrumentationName) {
    this.instrumentationName = instrumentationName;
    return this;
  }

  @CanIgnoreReturnValue
  public CassandraTelemetryBuilder setStatementSanitizationEnabled(boolean enabled) {
    this.statementSanitizationEnabled = enabled;
    return this;
  }

  public CassandraTelemetry build() {
    return new CassandraTelemetry(openTelemetry, instrumentationName, statementSanitizationEnabled);
  }
}
