/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.RpcClientTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class RmiClientTracer extends RpcClientTracer {
  private static final RmiClientTracer TRACER = new RmiClientTracer();

  public static RmiClientTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Method method) {
    String serviceName = method.getDeclaringClass().getName();
    String methodName = method.getName();

    Span span =
        spanBuilder(serviceName + "/" + methodName, CLIENT)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, getRpcSystem())
            .setAttribute(SemanticAttributes.RPC_SERVICE, serviceName)
            .setAttribute(SemanticAttributes.RPC_METHOD, methodName)
            .startSpan();

    return Context.current().with(span);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.rmi";
  }

  @Override
  protected String getRpcSystem() {
    return "java_rmi";
  }
}
