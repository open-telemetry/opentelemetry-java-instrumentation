/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.javax.response;

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
    return "io.opentelemetry.javaagent.servlet-javax-common";
  }

  public Context startSpan(Method method) {
    return startSpan(SpanNames.fromMethod(method));
  }
}
