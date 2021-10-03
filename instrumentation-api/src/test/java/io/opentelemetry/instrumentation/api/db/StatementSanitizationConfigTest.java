/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

public class StatementSanitizationConfigTest {

  @Test
  void shouldGetFalse() {
    assertFalse(StatementSanitizationConfig.isStatementSanitizationEnabled());
  }
}
