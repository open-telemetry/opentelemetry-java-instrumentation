/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;

public class GrailsTracer extends BaseTracer {

  private static final GrailsTracer TRACER = new GrailsTracer();

  public static GrailsTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Object controller, String action) {
    return startSpan(spanNameForClass(controller.getClass()) + "." + action);
  }

  public void updateServerSpanName(Context context, GrailsControllerUrlMappingInfo info) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan != null) {
      ServerSpanNaming serverSpanNaming = ServerSpanNaming.from(context);
      if (serverSpanNaming.shouldControllerUpdateServerSpanName()) {
        String action =
            info.getActionName() != null
                ? info.getActionName()
                : info.getControllerClass().getDefaultAction();
        serverSpan.updateName(
            ServletContextPath.prepend(context, "/" + info.getControllerName() + "/" + action));
        serverSpanNaming.setControllerUpdatedServerSpanName();
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.grails-3.0";
  }
}
