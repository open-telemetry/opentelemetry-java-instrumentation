/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.InvocationTargetException;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;

public class GrailsTracer extends BaseTracer {

  private static final GrailsTracer TRACER = new GrailsTracer();

  public static GrailsTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Object controller, String action) {
    return startSpan(spanNameForClass(controller.getClass()) + "." + action);
  }

  public void nameServerSpan(Span serverSpan, GrailsControllerUrlMappingInfo info) {
    String action =
        info.getActionName() != null
            ? info.getActionName()
            : info.getControllerClass().getDefaultAction();
    serverSpan.updateName(
        ServletContextPath.prepend(
            Context.current(), "/" + info.getControllerName() + "/" + action));
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof InvocationTargetException) {
      return throwable.getCause();
    }
    return super.unwrapThrowable(throwable);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.grails-3.0";
  }
}
