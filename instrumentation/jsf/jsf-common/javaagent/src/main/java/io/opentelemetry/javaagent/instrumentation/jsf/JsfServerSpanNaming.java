/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource.CONTROLLER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.server.ServerSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;

public class JsfServerSpanNaming {

  private static final boolean USE_VIEW_NAME_AS_HTTP_ROUTE =
      ExperimentalConfig.get().useViewNameAsHttpRoute();

  private static final HttpRouteGetter<FacesContext> SERVER_SPAN_NAME =
      (context, facesContext) -> {
        UIViewRoot uiViewRoot = facesContext.getViewRoot();
        if (uiViewRoot == null) {
          return null;
        }

        // JSF spec 7.6.2
        // view id is a context relative path to the web application resource that produces the
        // view, such as a JSP page or a Facelets page.
        String viewId = uiViewRoot.getViewId();
        return ServletContextPath.prepend(context, viewId);
      };

  public static void updateViewName(Context context, FacesContext facesContext) {
    if (USE_VIEW_NAME_AS_HTTP_ROUTE) {
      HttpRouteHolder.updateHttpRoute(
          context, CONTROLLER, JsfServerSpanNaming.SERVER_SPAN_NAME, facesContext);
    } else {
      // just update the server span name, without touching the http.route
      Span serverSpan = ServerSpan.fromContextOrNull(context);
      if (serverSpan == null) {
        return;
      }
      String name = SERVER_SPAN_NAME.get(context, facesContext);
      if (name != null) {
        serverSpan.updateName(name);
      }
    }
  }

  private JsfServerSpanNaming() {}
}
