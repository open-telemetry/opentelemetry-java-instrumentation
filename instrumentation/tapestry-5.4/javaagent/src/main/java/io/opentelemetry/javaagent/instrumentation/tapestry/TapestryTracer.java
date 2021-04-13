/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import org.apache.tapestry5.runtime.ComponentEventException;

public class TapestryTracer extends BaseTracer {

  private static final TapestryTracer TRACER = new TapestryTracer();

  public static TapestryTracer tracer() {
    return TRACER;
  }

  private TapestryTracer() {
    super(GlobalOpenTelemetry.get());
  }

  public void updateServerSpanName(String pageName) {
    if (pageName == null) {
      return;
    }
    Context context = Context.current();
    Span span = ServerSpan.fromContextOrNull(context);
    if (span != null) {
      if (!pageName.isEmpty()) {
        pageName = "/" + pageName;
      }
      span.updateName(ServletContextPath.prepend(context, pageName));
    }
  }

  public Context startEventSpan(String eventType, String componentId) {
    return super.startSpan(eventType + "/" + componentId);
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof ComponentEventException) {
      throwable = throwable.getCause();
    }
    return super.unwrapThrowable(throwable);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.tapestry-5.4";
  }
}
