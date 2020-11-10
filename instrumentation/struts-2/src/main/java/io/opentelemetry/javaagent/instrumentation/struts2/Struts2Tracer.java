/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import com.opensymphony.xwork2.ActionInvocation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class Struts2Tracer extends BaseTracer {

  public static final Struts2Tracer TRACER = new Struts2Tracer();

  public Span startSpan(ActionInvocation actionInvocation) {
    Object action = actionInvocation.getAction();
    Class<?> actionClass = action.getClass();

    String method = actionInvocation.getProxy().getMethod();

    String spanName = spanNameForMethod(actionClass, method);

    Span strutsSpan = tracer.spanBuilder(spanName).startSpan();

    strutsSpan.setAttribute("code.namespace", actionClass.getName());
    if (method != null) {
      strutsSpan.setAttribute("code.function", method);
    }

    return strutsSpan;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.struts-2";
  }
}
