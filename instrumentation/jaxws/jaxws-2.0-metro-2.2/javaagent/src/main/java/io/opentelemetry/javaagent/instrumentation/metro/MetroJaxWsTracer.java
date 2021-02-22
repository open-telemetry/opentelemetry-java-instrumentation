/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.WSEndpoint;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.metro.TracingPropertySet.ThrowableHolder;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.handler.MessageContext;

public class MetroJaxWsTracer extends BaseTracer {
  private static final MetroJaxWsTracer TRACER = new MetroJaxWsTracer();

  public static MetroJaxWsTracer tracer() {
    return TRACER;
  }

  public void startSpan(WSEndpoint endpoint, Packet packet) {
    String serviceName = endpoint.getServiceName().getLocalPart();
    String operationName = packet.getWSDLOperation().getLocalPart();
    String spanName = serviceName + "/" + operationName;
    Context context = startSpan(spanName, INTERNAL);
    Scope scope = context.makeCurrent();

    // store context and scope
    packet.addSatellite(new TracingPropertySet(context, scope));

    Span serverSpan = getCurrentServerSpan();
    if (serverSpan != null) {
      String serverSpanName = spanName;
      HttpServletRequest request = (HttpServletRequest) packet.get(MessageContext.SERVLET_REQUEST);
      if (request != null) {
        String servletPath = request.getServletPath();
        if (!servletPath.isEmpty()) {
          String pathInfo = request.getPathInfo();
          if (pathInfo != null) {
            serverSpanName = servletPath + "/" + spanName;
          } else {
            // when pathInfo is null then there is a servlet that is mapped to this exact service
            // servletPath already contains the service name
            serverSpanName = servletPath + "/" + operationName;
          }
        }
      }
      serverSpan.updateName(ServletContextPath.prepend(context, serverSpanName));
    }
  }

  public void end(Packet packet) {
    end(packet, null);
  }

  public void end(Packet packet, Throwable throwable) {
    Scope scope = (Scope) packet.get(TracingPropertySet.SCOPE_KEY);
    if (scope != null) {
      scope.close();

      Context context = (Context) packet.get(TracingPropertySet.CONTEXT_KEY);
      if (throwable == null) {
        ThrowableHolder throwableHolder =
            (ThrowableHolder) packet.get(TracingPropertySet.THROWABLE_KEY);
        throwable = throwableHolder.getThrowable();
      }
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      } else {
        tracer().end(context);
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.metro";
  }
}
