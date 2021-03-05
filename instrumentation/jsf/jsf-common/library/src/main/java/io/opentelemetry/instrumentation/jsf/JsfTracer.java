/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsf;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import javax.faces.FacesException;
import javax.faces.component.ActionSource2;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;

public abstract class JsfTracer extends BaseTracer {

  public Context startSpan(ActionEvent event) {
    // https://jakarta.ee/specifications/faces/2.3/apidocs/index.html?javax/faces/component/ActionSource2.html
    // ActionSource2 was added in JSF 1.2 and is implemented by components that have an action
    // attribute such as a button or a link
    if (event.getComponent() instanceof ActionSource2) {
      ActionSource2 actionSource = (ActionSource2) event.getComponent();
      if (actionSource.getActionExpression() != null) {
        // either an el expression in the form #{bean.method()} or navigation case name
        String expressionString = actionSource.getActionExpression().getExpressionString();
        // start span only if expression string is really an expression
        if (expressionString.startsWith("#{") || expressionString.startsWith("${")) {
          return startSpan(expressionString);
        }
      }
    }

    return null;
  }

  public void updateServerSpanName(Context context, FacesContext facesContext) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    UIViewRoot uiViewRoot = facesContext.getViewRoot();
    if (uiViewRoot == null) {
      return;
    }

    // JSF spec 7.6.2
    // view id is a context relative path to the web application resource that produces the view,
    // such as a JSP page or a Facelets page.
    String viewId = uiViewRoot.getViewId();
    serverSpan.updateName(ServletContextPath.prepend(context, viewId));
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    while (throwable.getCause() != null && throwable instanceof FacesException) {
      throwable = throwable.getCause();
    }

    return super.unwrapThrowable(throwable);
  }
}
