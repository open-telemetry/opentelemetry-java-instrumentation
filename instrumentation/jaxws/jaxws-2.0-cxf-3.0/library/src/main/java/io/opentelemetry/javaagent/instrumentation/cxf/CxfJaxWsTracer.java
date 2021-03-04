/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import javax.servlet.http.HttpServletRequest;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.BindingOperationInfo;

public class CxfJaxWsTracer extends BaseTracer {
  private static final String CONTEXT_KEY = CxfJaxWsTracer.class.getName() + ".Context";
  private static final String SCOPE_KEY = CxfJaxWsTracer.class.getName() + ".Scope";
  private static final CxfJaxWsTracer TRACER = new CxfJaxWsTracer();

  public static CxfJaxWsTracer tracer() {
    return TRACER;
  }

  public void startSpan(Message message) {
    Exchange exchange = message.getExchange();
    BindingOperationInfo bindingOperationInfo = exchange.get(BindingOperationInfo.class);
    if (bindingOperationInfo == null) {
      return;
    }

    String serviceName = bindingOperationInfo.getBinding().getService().getName().getLocalPart();
    String operationName = bindingOperationInfo.getOperationInfo().getName().getLocalPart();
    String spanName = serviceName + "/" + operationName;
    Context context = startSpan(spanName, INTERNAL);
    Scope scope = context.makeCurrent();

    exchange.put(CONTEXT_KEY, context);
    exchange.put(SCOPE_KEY, scope);

    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan != null) {
      String serverSpanName = spanName;
      HttpServletRequest request = (HttpServletRequest) message.get("HTTP.REQUEST");
      if (request != null) {
        String servletPath = request.getServletPath();
        if (!servletPath.isEmpty()) {
          serverSpanName = servletPath + "/" + spanName;
        }
      }
      serverSpan.updateName(ServletContextPath.prepend(context, serverSpanName));
    }
  }

  public void stopSpan(Message message) {
    Exchange exchange = message.getExchange();
    Scope scope = (Scope) exchange.remove(SCOPE_KEY);
    if (scope == null) {
      return;
    }
    scope.close();
    Context context = (Context) exchange.remove(CONTEXT_KEY);

    Throwable throwable = message.getContent(Exception.class);
    if (throwable instanceof Fault && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
    } else {
      tracer().end(context);
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jaxws-2.0-cxf-3.0";
  }
}
