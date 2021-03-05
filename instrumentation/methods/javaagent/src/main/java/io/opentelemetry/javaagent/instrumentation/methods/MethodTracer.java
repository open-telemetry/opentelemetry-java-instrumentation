/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;

public class MethodTracer extends BaseTracer {
  private static final MethodTracer TRACER = new MethodTracer();

  public static MethodTracer tracer() {
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
