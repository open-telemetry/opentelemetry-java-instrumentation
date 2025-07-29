/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;

public final class FieldBackedImplementationConfiguration {
  static boolean fieldInjectionEnabled = true;

  public static void configure(EarlyInitAgentConfig config) {
    fieldInjectionEnabled =
        config.getBoolean("otel.javaagent.experimental.field-injection.enabled", true);
  }

  private FieldBackedImplementationConfiguration() {}
}
