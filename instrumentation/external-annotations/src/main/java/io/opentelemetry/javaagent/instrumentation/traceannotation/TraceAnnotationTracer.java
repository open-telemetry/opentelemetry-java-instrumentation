/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.traceannotation;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class TraceAnnotationTracer extends BaseTracer {
  private static final TraceAnnotationTracer TRACER = new TraceAnnotationTracer();

  public static TraceAnnotationTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.trace-annotation";
  }
}
