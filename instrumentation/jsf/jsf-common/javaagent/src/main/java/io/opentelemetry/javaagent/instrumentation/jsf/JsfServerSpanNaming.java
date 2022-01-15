/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf;

import io.opentelemetry.instrumentation.api.server.ServerSpanNameSupplier;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;

public class JsfServerSpanNaming {

  public static final ServerSpanNameSupplier<FacesContext> SERVER_SPAN_NAME =
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

  private JsfServerSpanNaming() {}
}
