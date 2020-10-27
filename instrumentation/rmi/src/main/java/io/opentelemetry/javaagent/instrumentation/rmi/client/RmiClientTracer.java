/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.instrumentation.api.tracer.RpcClientTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class RmiClientTracer extends RpcClientTracer {
  public static final RmiClientTracer TRACER = new RmiClientTracer();

  public Span startSpan(Method method) {
    String serviceName = method.getDeclaringClass().getName();
    String methodName = method.getName();

    Span.Builder spanBuilder =
        tracer.spanBuilder(serviceName + "/" + methodName).setSpanKind(CLIENT);
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "java_rmi");
    spanBuilder.setAttribute(SemanticAttributes.RPC_SERVICE, serviceName);
    spanBuilder.setAttribute(SemanticAttributes.RPC_METHOD, methodName);

    return spanBuilder.startSpan();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.rmi";
  }
}
