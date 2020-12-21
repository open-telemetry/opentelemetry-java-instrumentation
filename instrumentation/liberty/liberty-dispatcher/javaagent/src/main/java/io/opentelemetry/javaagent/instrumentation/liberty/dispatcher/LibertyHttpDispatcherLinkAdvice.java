/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import static io.opentelemetry.javaagent.instrumentation.liberty.dispatcher.LibertyDispatcherTracer.tracer;

import com.ibm.ws.http.channel.internal.inbound.HttpInboundServiceContextImpl;
import com.ibm.ws.http.dispatcher.internal.channel.HttpDispatcherLink;
import com.ibm.wsspi.http.channel.values.StatusCodes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

/**
 * Instrumenting
 * https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/ws/http/dispatcher/internal/channel/HttpDispatcherLink.java
 * We instrument sendResponse method that is called when no application has been deployed under
 * requested context root or something goes horribly wrong and server responds with Internal Server
 * Error
 */
@SuppressWarnings("unused")
public class LibertyHttpDispatcherLinkAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This HttpDispatcherLink httpDispatcherLink,
      @Advice.FieldValue("isc") HttpInboundServiceContextImpl isc,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    LibertyRequestWrapper requestWrapper =
        new LibertyRequestWrapper(httpDispatcherLink, isc.getRequest());
    LibertyConnectionWrapper connectionWrapper =
        new LibertyConnectionWrapper(httpDispatcherLink, isc.getRequest());
    context =
        tracer()
            .startSpan(
                requestWrapper, connectionWrapper, null, "HTTP " + requestWrapper.getMethod());
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable,
      @Advice.Argument(value = 0) StatusCodes statusCode,
      @Advice.Argument(value = 2) Exception failure,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    LibertyResponseWrapper responseWrapper = new LibertyResponseWrapper(statusCode);

    Throwable t = failure != null ? failure : throwable;
    if (t != null) {
      tracer().endExceptionally(context, t, responseWrapper);
      return;
    }

    tracer().end(context, responseWrapper);
  }
}
