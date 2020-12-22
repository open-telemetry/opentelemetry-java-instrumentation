/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.javaagent.tooling.OpenTelemetryInstaller;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanProcessor;

public class TestOpenTelemetryInstaller extends OpenTelemetryInstaller {

  private final SpanProcessor spanProcessor;

  public TestOpenTelemetryInstaller(SpanProcessor spanProcessor) {
    this.spanProcessor = spanProcessor;
  }

  @Override
  public void afterByteBuddyAgent() {
    // TODO this is probably temporary until default propagators are supplied by SDK
    //  https://github.com/open-telemetry/opentelemetry-java/issues/1742
    //  currently checking against no-op implementation so that it won't override aws-lambda
    //  propagator configuration
    if (OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .getClass()
        .getSimpleName()
        .equals("NoopTextMapPropagator")) {
      // Workaround https://github.com/open-telemetry/opentelemetry-java/pull/2096
      OpenTelemetry.setGlobalPropagators(
          ContextPropagators.create(W3CTraceContextPropagator.getInstance()));
    }
    OpenTelemetrySdk.getGlobalTracerManagement().addSpanProcessor(spanProcessor);
  }
}
