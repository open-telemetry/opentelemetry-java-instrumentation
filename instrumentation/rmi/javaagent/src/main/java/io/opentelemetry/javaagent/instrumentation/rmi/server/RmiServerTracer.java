/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import static io.opentelemetry.api.trace.SpanKind.SERVER;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class RmiServerTracer extends RpcServerTracer {
  private static final RmiServerTracer TRACER = new RmiServerTracer();

  public static RmiServerTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Context parentContext, Method method) {
    String serviceName = method.getDeclaringClass().getName();
    String methodName = method.getName();

    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(serviceName + "/" + methodName)
            .setSpanKind(SERVER)
            .setParent(parentContext)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, "java_rmi")
            .setAttribute(SemanticAttributes.RPC_SERVICE, serviceName)
            .setAttribute(SemanticAttributes.RPC_METHOD, methodName);
    return parentContext.with(spanBuilder.startSpan());
  }

  @Override
  protected TextMapGetter getGetter() {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.rmi";
  }
}
