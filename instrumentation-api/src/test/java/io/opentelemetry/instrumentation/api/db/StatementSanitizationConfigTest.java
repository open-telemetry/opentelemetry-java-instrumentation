/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.opentelemetry.instrumentation.api.config.Config;
import org.junit.jupiter.api.Test;

public class StatementSanitizationConfigTest {

  @Test
  void shouldGetFalse() {
    Config config = Config.newBuilder()
            .addProperty("otel.instrumentation.common.db-statement-sanitizer.enabled", "false")
            .build();
    Config.internalInitializeConfig(config);
    assertFalse(StatementSanitizationConfig.isStatementSanitizationEnabled());
  }
}
