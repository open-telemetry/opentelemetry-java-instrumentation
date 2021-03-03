/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class JaxWsTracer extends BaseTracer {

  private static final JaxWsTracer TRACER = new JaxWsTracer();

  public static JaxWsTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jaxws-common";
  }

  public Context startSpan(Class<?> target, Method method) {
    String spanName = spanNameForMethod(target, method);

    Context parentContext = Context.current();
    Span serverSpan = ServerSpan.fromContextOrNull(parentContext);
    if (serverSpan != null) {
      serverSpan.updateName(spanName);
    }

    return parentContext.with(
        tracer
            .spanBuilder(spanName)
            .setParent(parentContext)
            .setAttribute(SemanticAttributes.CODE_NAMESPACE, method.getDeclaringClass().getName())
            .setAttribute(SemanticAttributes.CODE_FUNCTION, method.getName())
            .startSpan());
  }
}
