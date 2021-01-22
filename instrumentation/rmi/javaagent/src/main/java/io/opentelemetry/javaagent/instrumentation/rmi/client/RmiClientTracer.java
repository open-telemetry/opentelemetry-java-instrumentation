/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.instrumentation.api.tracer.RpcClientTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class RmiClientTracer extends RpcClientTracer {
  private static final RmiClientTracer TRACER = new RmiClientTracer();

  public static RmiClientTracer tracer() {
    return TRACER;
  }

  @Override
  public Span startSpan(Method method) {
    String serviceName = method.getDeclaringClass().getName();
    String methodName = method.getName();

    SpanBuilder spanBuilder =
        tracer.spanBuilder(serviceName + "/" + methodName).setSpanKind(CLIENT);
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "java_rmi");
    spanBuilder.setAttribute(SemanticAttributes.RPC_SERVICE, serviceName);
    spanBuilder.setAttribute(SemanticAttributes.RPC_METHOD, methodName);

    return spanBuilder.startSpan();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.rmi";
  }
}
