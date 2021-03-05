/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;

public class HttpServletResponseTracer extends BaseTracer {
  private static final HttpServletResponseTracer TRACER = new HttpServletResponseTracer();

  public static HttpServletResponseTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-common";
  }

  public Context startSpan(Method method) {
    return startSpan(spanNameForMethod(method));
  }
}
