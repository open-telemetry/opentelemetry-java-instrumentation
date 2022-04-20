/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;

public final class JsfServerSpanNaming {

  public static void updateViewName(Context context, FacesContext facesContext) {
    // just update the server span name, without touching the http.route
    Span serverSpan = LocalRootSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    UIViewRoot uiViewRoot = facesContext.getViewRoot();
    if (uiViewRoot == null) {
      return;
    }

    // JSF spec 7.6.2
    // view id is a context relative path to the web application resource that produces the
    // view, such as a JSP page or a Facelets page.
    String viewId = uiViewRoot.getViewId();
    String name = ServletContextPath.prepend(context, viewId);
    serverSpan.updateName(name);
  }

  private JsfServerSpanNaming() {}
}
