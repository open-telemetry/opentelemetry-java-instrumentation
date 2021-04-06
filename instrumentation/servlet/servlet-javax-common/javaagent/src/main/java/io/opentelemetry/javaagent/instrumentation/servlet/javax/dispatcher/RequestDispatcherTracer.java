/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.javax.dispatcher;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;

public class RequestDispatcherTracer extends BaseTracer {
  private static final RequestDispatcherTracer TRACER = new RequestDispatcherTracer();

  public static RequestDispatcherTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-javax-common";
  }

  public Context startSpan(Context parentContext, Method method) {
    return startSpan(parentContext, spanNameForMethod(method), SpanKind.INTERNAL);
  }
}
