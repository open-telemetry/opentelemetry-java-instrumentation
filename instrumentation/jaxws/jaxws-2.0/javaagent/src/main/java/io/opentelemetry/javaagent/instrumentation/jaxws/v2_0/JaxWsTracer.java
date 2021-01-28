/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;

public class JaxWsTracer extends BaseTracer {

  private static final JaxWsTracer TRACER = new JaxWsTracer();

  public static JaxWsTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jaxws";
  }

  public Span startSpan(Class<?> target, Method method) {
    String pathBasedSpanName = spanNameForMethod(target, method);
    Context context = Context.current();
    Span serverSpan = BaseTracer.getCurrentServerSpan(context);

    // When jax-rs is the root, we want to name using the path, otherwise use the class/method.
    String spanName;
    if (serverSpan == null) {
      spanName = pathBasedSpanName;
    } else {
      spanName = spanNameForMethod(target, method);
      updateServerSpanName(context, serverSpan, pathBasedSpanName);
    }

    return tracer
        .spanBuilder(spanName)
        .setAttribute(SemanticAttributes.CODE_NAMESPACE, method.getDeclaringClass().getName())
        .setAttribute(SemanticAttributes.CODE_FUNCTION, method.getName())
        .startSpan();
  }

  private void updateServerSpanName(Context context, Span span, String spanName) {
    if (!spanName.isEmpty()) {
      span.updateName(ServletContextPath.prepend(context, spanName));
    }
  }
}
