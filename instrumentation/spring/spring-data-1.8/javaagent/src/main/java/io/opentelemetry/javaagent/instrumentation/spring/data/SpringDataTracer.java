/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;

public final class SpringDataTracer extends BaseTracer {
  private static final SpringDataTracer TRACER = new SpringDataTracer();

  public static SpringDataTracer tracer() {
    return TRACER;
  }

  private SpringDataTracer() {}

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-data-1.8";
  }

  public Context startSpan(Method method) {
    return startSpan(spanNameForMethod(method));
  }
}
