/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import java.lang.reflect.Method;

public class ResponseTracer extends BaseTracer {
  private static final ResponseTracer TRACER = new ResponseTracer();

  public static ResponseTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.servlet-5.0";
  }

  public Context startSpan(Method method) {
    return startSpan(SpanNames.fromMethod(method));
  }
}
