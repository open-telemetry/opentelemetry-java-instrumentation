/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionProxy;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class Struts2Tracer extends BaseTracer {

  public static final Struts2Tracer TRACER = new Struts2Tracer();

  public Span startSpan(ActionInvocation actionInvocation) {
//    updateHttpRouteInServerSpan(actionInvocation);

    Object action = actionInvocation.getAction();
    String namespace = action.getClass().getName();

    String method = actionInvocation.getProxy().getMethod();

    String spanName = spanNameForMethod(action.getClass(), method);

    Span strutsSpan = tracer.spanBuilder(spanName).startSpan();

    strutsSpan.setAttribute("code.namespace", namespace);
    if (method != null) {
      strutsSpan.setAttribute("code.function", method);
    }

    return strutsSpan;
  }

  private void updateHttpRouteInServerSpan(ActionInvocation actionInvocation) {
    ActionProxy proxy = actionInvocation.getProxy();
    String method = proxy.getMethod();
    String httpRoute =
        proxy.getNamespace() + proxy.getActionName() + (method != null ? "." + method : "");
    Span serverSpan = getCurrentServerSpan();
    serverSpan.setAttribute("http.route", httpRoute);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.struts-2";
  }
}
