/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlyHttpServerTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;

public class HttpCodecFilterAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Origin Method method,
      @Advice.Argument(0) FilterChainContext ctx,
      @Advice.Argument(1) HttpHeader httpHeader) {
    Context context = tracer().getServerContext(ctx);

    // only create a span if there isn't another one attached to the current ctx
    // and if the httpHeader has been parsed into a HttpRequestPacket
    if (context != null || !(httpHeader instanceof HttpRequestPacket)) {
      return;
    }
    HttpRequestPacket httpRequest = (HttpRequestPacket) httpHeader;
    Context extractedContext = tracer().startSpan(httpRequest, httpRequest, method);
    Span span = Java8BytecodeBridge.spanFromContext(extractedContext);

    // We don't actually want to attach new context to this thread, as actual request will continue
    // on some other thread. But we do want to attach that new context to FilterChainContext
    tracer().startScope(span, ctx).close();
  }
}
