/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardviews;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;

public class DropwizardTracer extends BaseInstrumenter {
  private static final DropwizardTracer TRACER = new DropwizardTracer();

  public static DropwizardTracer tracer() {
    return TRACER;
  }

  public Span startSpan(String spanName) {
    return io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext(
        super.startOperation(spanName, Span.Kind.INTERNAL));
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.dropwizard-views";
  }
}
