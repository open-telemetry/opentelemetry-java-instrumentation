/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation;

import io.opentelemetry.context.propagation.ContextPropagators;

public class ApplicationContextPropagators
    implements application.io.opentelemetry.context.propagation.ContextPropagators {

  private final ApplicationTextMapPropagator applicationTextMapPropagator;

  public ApplicationContextPropagators(ContextPropagators agentPropagators) {
    applicationTextMapPropagator =
        new ApplicationTextMapPropagator(agentPropagators.getTextMapPropagator());
  }

  @Override
  public application.io.opentelemetry.context.propagation.TextMapPropagator getTextMapPropagator() {
    return applicationTextMapPropagator;
  }
}
