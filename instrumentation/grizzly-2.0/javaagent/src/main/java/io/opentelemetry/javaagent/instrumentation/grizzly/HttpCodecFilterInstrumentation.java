/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlyHttpServerTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacketParsing;
import org.glassfish.grizzly.http.HttpRequestPacket;

public class HttpCodecFilterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.http.HttpCodecFilter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // this is for 2.3.20+
    transformer.applyAdviceToMethod(
        named("handleRead")
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.HttpHeader")))
            .and(isPublic()),
        HttpCodecFilterInstrumentation.class.getName() + "$HandleReadAdvice");
    // this is for 2.3 through 2.3.19
    transformer.applyAdviceToMethod(
        named("handleRead")
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.HttpPacketParsing")))
            .and(isPublic()),
        HttpCodecFilterInstrumentation.class.getName() + "$HandleReadOldAdvice");
  }

  public static class HandleReadAdvice {

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

      // We don't want to attach the new context to this thread, as actual request will continue on
      // some other thread where we will read and attach it via tracer().getServerContext(ctx).
      tracer().startSpan(httpRequest, httpRequest, ctx, method);
    }
  }

  public static class HandleReadOldAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Origin Method method,
        @Advice.Argument(0) FilterChainContext ctx,
        @Advice.Argument(1) HttpPacketParsing httpHeader) {
      Context context = tracer().getServerContext(ctx);

      // only create a span if there isn't another one attached to the current ctx
      // and if the httpHeader has been parsed into a HttpRequestPacket
      if (context != null || !(httpHeader instanceof HttpRequestPacket)) {
        return;
      }
      HttpRequestPacket httpRequest = (HttpRequestPacket) httpHeader;

      // We don't want to attach the new context to this thread, as actual request will continue on
      // some other thread where we will read and attach it via tracer().getServerContext(ctx).
      tracer().startSpan(httpRequest, httpRequest, ctx, method);
    }
  }
}
