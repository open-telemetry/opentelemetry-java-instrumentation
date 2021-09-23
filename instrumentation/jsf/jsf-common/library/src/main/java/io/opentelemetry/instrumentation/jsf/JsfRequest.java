/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsf;

import java.util.Objects;
import javax.faces.component.ActionSource2;
import javax.faces.event.ActionEvent;

public class JsfRequest {
  private final String spanName;

  public JsfRequest(ActionEvent event) {
    this.spanName = getSpanName(event);
  }

  public String spanName() {
    return Objects.requireNonNull(spanName);
  }

  public boolean shouldStartSpan() {
    return spanName != null;
  }

  private static String getSpanName(ActionEvent event) {
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
          return expressionString;
        }
      }
    }

    return null;
  }
}
