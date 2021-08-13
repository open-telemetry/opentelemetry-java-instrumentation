/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsf;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import java.util.function.Supplier;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;

public class JsfServerSpanNaming {

  public static void updateServerSpanName(FacesContext facesContext) {
    Context parentContext = Context.current();
    ServerSpanNaming.updateServerSpanName(
        parentContext, CONTROLLER, getServerSpanNameSupplier(parentContext, facesContext));
  }

  private static Supplier<String> getServerSpanNameSupplier(
      Context context, FacesContext facesContext) {
    return () -> getServerSpanName(context, facesContext);
  }

  private static String getServerSpanName(Context context, FacesContext facesContext) {
    UIViewRoot uiViewRoot = facesContext.getViewRoot();
    if (uiViewRoot == null) {
      return null;
    }

    // JSF spec 7.6.2
    // view id is a context relative path to the web application resource that produces the view,
    // such as a JSP page or a Facelets page.
    String viewId = uiViewRoot.getViewId();
    return ServletContextPath.prepend(context, viewId);
  }
}
