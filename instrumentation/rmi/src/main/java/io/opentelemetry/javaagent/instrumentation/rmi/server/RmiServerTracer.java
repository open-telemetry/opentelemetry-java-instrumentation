/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.rmi.server;

import static io.opentelemetry.trace.Span.Kind.SERVER;

import io.grpc.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Builder;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class RmiServerTracer extends RpcServerTracer {
  public static final RmiServerTracer TRACER = new RmiServerTracer();

  public Span startSpan(Method method, Context context) {
    String serviceName = method.getDeclaringClass().getName();
    String methodName = method.getName();

    Builder spanBuilder =
        tracer.spanBuilder(serviceName + "/" + methodName).setSpanKind(SERVER).setParent(context);
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "java_rmi");
    spanBuilder.setAttribute(SemanticAttributes.RPC_SERVICE, serviceName);
    spanBuilder.setAttribute(SemanticAttributes.RPC_METHOD, methodName);

    return spanBuilder.startSpan();
  }

  @Override
  protected Getter getGetter() {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.rmi";
  }
}
