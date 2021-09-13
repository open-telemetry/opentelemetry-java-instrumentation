/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation;

import application.io.opentelemetry.context.propagation.ContextPropagators;
import application.io.opentelemetry.context.propagation.TextMapPropagator;

public class ApplicationContextPropagators implements ContextPropagators {

  private final ApplicationTextMapPropagator applicationTextMapPropagator;

  public ApplicationContextPropagators(
      io.opentelemetry.context.propagation.ContextPropagators agentPropagators) {
    applicationTextMapPropagator =
        new ApplicationTextMapPropagator(agentPropagators.getTextMapPropagator());
  }

  @Override
  public TextMapPropagator getTextMapPropagator() {
    return applicationTextMapPropagator;
  }
}
