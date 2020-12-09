/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;

public class TraceAnnotationTracer extends BaseInstrumenter {
  private static final TraceAnnotationTracer TRACER = new TraceAnnotationTracer();

  public static TraceAnnotationTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.external-annotations";
  }
}
