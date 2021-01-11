/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import javax.faces.FacesException;
import javax.faces.component.ActionSource2;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.el.EvaluationException;
import javax.faces.el.MethodNotFoundException;
import javax.faces.event.ActionEvent;

public abstract class JsfTracer extends BaseTracer {

  private static String getSpanName(String expressionString) {
    String result = expressionString;
    if (result.startsWith("#{")) {
      result = result.substring(2);
    }

    if (result.endsWith("}")) {
      result = result.substring(0, result.length() - 1);
    }

    if (result.contains("(") && result.contains(")")) {
      result = result.replaceAll("\\(.*?\\)", "()");
    }

    return result;
  }

  public Span startSpan(ActionEvent event) {
    if (event.getComponent() instanceof ActionSource2) {
      ActionSource2 actionSource = (ActionSource2) event.getComponent();
      if (actionSource.getActionExpression() != null) {
        String expressionString = actionSource.getActionExpression().getExpressionString();
        String spanName = getSpanName(expressionString);
        return tracer.spanBuilder(spanName).startSpan();
      }
    }

    return null;
  }

  public void updateServerSpanName(Context context, FacesContext facesContext) {
    Span serverSpan = getCurrentServerSpan();
    if (serverSpan == null) {
      return;
    }

    UIViewRoot uiViewRoot = facesContext.getViewRoot();
    if (uiViewRoot == null) {
      return;
    }

    String viewId = uiViewRoot.getViewId();
    serverSpan.updateName(ServletContextPath.prepend(context, viewId));
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof FacesException) {
      Throwable cause = throwable.getCause();
      if (cause instanceof EvaluationException && cause.getCause() != null) {
        throwable = cause.getCause();
      } else if (cause instanceof MethodNotFoundException) {
        throwable = cause;
      }
    }
    return super.unwrapThrowable(throwable);
  }
}
