/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;

public final class FieldBackedImplementationConfiguration {
  static boolean fieldInjectionEnabled = true;

  public static void configure() {
    fieldInjectionEnabled = EarlyInitAgentConfig.get().isExperimentalFieldInjectionEnabled();
  }

  private FieldBackedImplementationConfiguration() {}
}
