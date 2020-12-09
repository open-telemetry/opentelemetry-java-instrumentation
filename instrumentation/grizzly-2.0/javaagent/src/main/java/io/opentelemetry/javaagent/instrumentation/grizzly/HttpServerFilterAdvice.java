/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlyHttpServerInstrumenter.tracer;

import io.opentelemetry.context.Context;
import net.bytebuddy.asm.Advice;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpResponsePacket;

public class HttpServerFilterAdvice {
  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Argument(0) FilterChainContext ctx, @Advice.Argument(2) HttpResponsePacket response) {
    Context context = tracer().getServerContext(ctx);
    if (context != null) {
      tracer().end(context, response);
    }
  }
}
