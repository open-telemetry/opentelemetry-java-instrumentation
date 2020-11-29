/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlyHttpServerTracer.tracer;

import io.opentelemetry.api.trace.Span;
import net.bytebuddy.asm.Advice;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpResponsePacket;

public class HttpServerFilterAdvice {
  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Argument(0) FilterChainContext ctx, @Advice.Argument(2) HttpResponsePacket response) {
    Span span = tracer().getServerSpan(ctx);
    if (span != null) {
      tracer().end(span, response);
    }
  }
}
