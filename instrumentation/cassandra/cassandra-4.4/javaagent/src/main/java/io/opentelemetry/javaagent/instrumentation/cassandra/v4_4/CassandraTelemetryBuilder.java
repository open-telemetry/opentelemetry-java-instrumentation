/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;

public class CassandraTelemetryBuilder
    extends io.opentelemetry.instrumentation.cassandra.CassandraTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean statementSanitizationEnabled = true;

  CassandraTelemetryBuilder(OpenTelemetry openTelemetry) {
    super(openTelemetry);
    this.openTelemetry = openTelemetry;
  }

  @Override
  @CanIgnoreReturnValue
  public CassandraTelemetryBuilder setStatementSanitizationEnabled(boolean enabled) {
    this.statementSanitizationEnabled = enabled;
    return this;
  }

  @Override
  public CassandraTelemetry build() {
    return new CassandraTelemetry(createInstrumenter(openTelemetry, statementSanitizationEnabled));
  }

  @Override
  protected String getInstrumenterName() {
    return "io.opentelemetry.cassandra-4.4";
  }
}
