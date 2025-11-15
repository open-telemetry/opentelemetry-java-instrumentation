/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.sqlcommenter;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.sqlcommenter.SqlCommenterCustomizer;

@AutoService(SqlCommenterCustomizer.class)
public class TestAgentSqlCommenterCustomizer implements SqlCommenterCustomizer {

  @Override
  public void customize(SqlCommenterBuilder builder) {
    if (Boolean.getBoolean("otel.testing.sqlcommenter.enabled")) {
      builder.setEnabled(true);
    }
  }
}
