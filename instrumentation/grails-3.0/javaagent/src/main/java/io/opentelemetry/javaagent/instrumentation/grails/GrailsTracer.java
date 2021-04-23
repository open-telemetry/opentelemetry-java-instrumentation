/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
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
    ServerSpanNaming.updateServerSpanName(
        context, CONTROLLER, () -> getServerSpanName(context, info));
  }

  private static String getServerSpanName(Context context, GrailsControllerUrlMappingInfo info) {
    String action =
        info.getActionName() != null
            ? info.getActionName()
            : info.getControllerClass().getDefaultAction();
    return ServletContextPath.prepend(context, "/" + info.getControllerName() + "/" + action);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.grails-3.0";
  }
}
