/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlySingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;

public class HttpCodecFilterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.http.HttpCodecFilter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleRead")
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(
                takesArgument(
                    1,
                    // this is for 2.3.20+
                    named("org.glassfish.grizzly.http.HttpHeader")
                        // this is for 2.3 through 2.3.19
                        .or(named("org.glassfish.grizzly.http.HttpPacketParsing"))))
            .and(isPublic()),
        HttpCodecFilterInstrumentation.class.getName() + "$HandleReadAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleReadAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Origin Method method,
        @Advice.Argument(0) FilterChainContext ctx,
        @Advice.Argument(1) Object httpHeader) {

      Context parentContext = GrizzlyStateStorage.getContext(ctx);
      if (parentContext == null) {
        parentContext = Java8BytecodeBridge.currentContext();
      }

      // don't create a span if the request hasn't been parsed yet
      if (!(httpHeader instanceof HttpRequestPacket)) {
        return;
      }

      HttpRequestPacket httpRequest = (HttpRequestPacket) httpHeader;
      if (!instrumenter().shouldStart(parentContext, httpRequest)) {
        return;
      }

      // We don't want to attach the new context to this thread, as actual request will continue on
      // some other thread where we will read and attach it via GrizzlyStateStorage.
      Context context = instrumenter().start(parentContext, httpRequest);
      GrizzlyStateStorage.attachContextAndRequest(ctx, context, httpRequest);
    }
  }
}
