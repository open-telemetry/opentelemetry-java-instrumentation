/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.armeria.v1_0;

import io.opentelemetry.javaagent.tooling.Instrumenter;

public abstract class AbstractArmeriaInstrumentation extends Instrumenter.Default {

  private static final String INSTRUMENTATION_NAME = "armeria";

  public AbstractArmeriaInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.armeria.v1_0.ArmeriaDecorators",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.ArmeriaClientTracer",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.ArmeriaClientTracer$ArmeriaSetter",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient$Decorator",
      // Corresponds to lambda when calling .thenAccept(log -> ...
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient$1",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.ArmeriaServerTracer",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.ArmeriaServerTracer$ArmeriaGetter",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService$Decorator",
      // Corresponds to lambda when calling .thenAccept(log -> ...
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService$1",
    };
  }
}
