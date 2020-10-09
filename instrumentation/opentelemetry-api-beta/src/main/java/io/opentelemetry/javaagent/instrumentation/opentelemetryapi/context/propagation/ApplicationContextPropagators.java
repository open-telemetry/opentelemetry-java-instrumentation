/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation;

import application.io.grpc.Context;
import application.io.opentelemetry.context.propagation.ContextPropagators;
import application.io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;

public class ApplicationContextPropagators implements ContextPropagators {

  private final ApplicationTextMapPropagator applicationTextMapPropagator;

  public ApplicationContextPropagators(ContextStore<Context, io.grpc.Context> contextStore) {
    applicationTextMapPropagator =
        new ApplicationTextMapPropagator(
            OpenTelemetry.getPropagators().getTextMapPropagator(), contextStore);
  }

  @Override
  public TextMapPropagator getTextMapPropagator() {
    return applicationTextMapPropagator;
  }
}
