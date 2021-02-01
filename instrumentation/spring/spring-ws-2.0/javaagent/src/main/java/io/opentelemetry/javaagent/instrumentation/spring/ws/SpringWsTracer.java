/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class SpringWsTracer extends BaseTracer {

  private static final SpringWsTracer TRACER = new SpringWsTracer();

  public static SpringWsTracer tracer() {
    return TRACER;
  }

  public Span startSpan(Method method) {
    Span springWsSpan = super.startSpan(method);
    springWsSpan.setAttribute(
        SemanticAttributes.CODE_NAMESPACE, method.getDeclaringClass().getName());
    springWsSpan.setAttribute(SemanticAttributes.CODE_FUNCTION, method.getName());

    return springWsSpan;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-ws";
  }
}
