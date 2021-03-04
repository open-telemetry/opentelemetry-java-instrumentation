/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.axis2;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import java.lang.reflect.InvocationTargetException;
import javax.servlet.http.HttpServletRequest;
import org.apache.axis2.jaxws.core.MessageContext;

public class Axis2JaxWsTracer extends BaseTracer {
  private static final String CONTEXT_KEY = Axis2JaxWsTracer.class.getName() + ".Context";
  private static final String SCOPE_KEY = Axis2JaxWsTracer.class.getName() + ".Scope";
  private static final Axis2JaxWsTracer TRACER = new Axis2JaxWsTracer();

  public static Axis2JaxWsTracer tracer() {
    return TRACER;
  }

  public void startSpan(MessageContext message) {
    org.apache.axis2.context.MessageContext axisMessageContext = message.getAxisMessageContext();
    String serviceName = axisMessageContext.getOperationContext().getServiceName();
    String operationName = axisMessageContext.getOperationContext().getOperationName();
    String spanName = serviceName + "/" + operationName;
    Context context = startSpan(spanName, INTERNAL);
    Scope scope = context.makeCurrent();

    message.setProperty(CONTEXT_KEY, context);
    message.setProperty(SCOPE_KEY, scope);

    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan != null) {
      String serverSpanName = spanName;
      HttpServletRequest request =
          (HttpServletRequest) message.getMEPContext().get("transport.http.servletRequest");
      if (request != null) {
        String servletPath = request.getServletPath();
        if (!servletPath.isEmpty()) {
          serverSpanName = servletPath + "/" + spanName;
        }
      }
      serverSpan.updateName(ServletContextPath.prepend(context, serverSpanName));
    }
  }

  public void end(MessageContext message) {
    end(message, null);
  }

  public void end(MessageContext message, Throwable throwable) {
    Scope scope = (Scope) message.getProperty(SCOPE_KEY);
    if (scope == null) {
      return;
    }

    scope.close();
    Context context = (Context) message.getProperty(CONTEXT_KEY);

    message.setProperty(CONTEXT_KEY, null);
    message.setProperty(SCOPE_KEY, null);

    if (throwable != null) {
      endExceptionally(context, throwable);
    } else {
      tracer().end(context);
    }
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof InvocationTargetException) {
      throwable = throwable.getCause();
    }
    return super.unwrapThrowable(throwable);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jaxws-2.0-axis2-1.6";
  }
}
