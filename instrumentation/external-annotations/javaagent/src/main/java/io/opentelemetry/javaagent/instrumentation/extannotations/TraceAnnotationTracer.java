/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;

public class TraceAnnotationTracer extends BaseTracer {
  private static final TraceAnnotationTracer TRACER = new TraceAnnotationTracer();

  public static TraceAnnotationTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.external-annotations";
  }

  public Context startSpan(Method method) {
    return startSpan(spanNameForMethod(method));
  }
}
