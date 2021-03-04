/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionProxy;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.servlet.ServletSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public class Struts2Tracer extends BaseTracer {

  private static final Struts2Tracer TRACER = new Struts2Tracer();

  public static Struts2Tracer tracer() {
    return TRACER;
  }

  public Context startSpan(Context parentContext, ActionInvocation actionInvocation) {
    Object action = actionInvocation.getAction();
    Class<?> actionClass = action.getClass();

    String method = actionInvocation.getProxy().getMethod();
    String spanName = spanNameForMethod(actionClass, method);

    SpanBuilder strutsSpan = tracer.spanBuilder(spanName).setParent(parentContext);

    strutsSpan.setAttribute(SemanticAttributes.CODE_NAMESPACE, actionClass.getName());
    if (method != null) {
      strutsSpan.setAttribute(SemanticAttributes.CODE_FUNCTION, method);
    }

    return parentContext.with(strutsSpan.startSpan());
  }

  // Handle cases where action parameters are encoded into URL path
  public void updateServerSpanName(Context context, ActionProxy actionProxy) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    // We take name from the config, because it contains the path pattern from the
    // configuration.
    String result = actionProxy.getConfig().getName();

    String actionNamespace = actionProxy.getNamespace();
    if (actionNamespace != null && !actionNamespace.isEmpty()) {
      if (actionNamespace.endsWith("/") || result.startsWith("/")) {
        result = actionNamespace + result;
      } else {
        result = actionNamespace + "/" + result;
      }
    }

    if (!result.startsWith("/")) {
      result = "/" + result;
    }

    serverSpan.updateName(ServletContextPath.prepend(context, result));
    // prevent servlet integration from doing further updates to server span name
    ServletSpanNaming.setServletUpdatedServerSpanName(context);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.struts-2.3";
  }
}
