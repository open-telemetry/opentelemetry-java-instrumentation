/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import io.opentelemetry.instrumentation.api.config.Config;

/** DB statement sanitization is always enabled by default, you have to manually disable it. */
final class StatementSanitizationConfig {

  private static final boolean STATEMENT_SANITIZATION_ENABLED =
      Config.get().getBoolean("otel.instrumentation.common.db-statement-sanitizer.enabled", true);

  static boolean isStatementSanitizationEnabled() {
    return STATEMENT_SANITIZATION_ENABLED;
  }

  private StatementSanitizationConfig() {}
}
