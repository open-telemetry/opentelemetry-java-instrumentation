/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlySingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

public class HttpServerFilterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.http.HttpServerFilter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("prepareResponse")
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.HttpRequestPacket")))
            .and(takesArgument(2, named("org.glassfish.grizzly.http.HttpResponsePacket")))
            .and(takesArgument(3, named("org.glassfish.grizzly.http.HttpContent")))
            .and(isPrivate()),
        HttpServerFilterInstrumentation.class.getName() + "$PrepareResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class PrepareResponseAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) FilterChainContext ctx,
        @Advice.Argument(2) HttpResponsePacket response) {
      Context context = GrizzlyStateStorage.removeContext(ctx);
      HttpRequestPacket request = GrizzlyStateStorage.removeRequest(ctx);
      if (context != null && request != null) {
        Throwable error = GrizzlyErrorHolder.getOrDefault(context, null);
        if (error == null) {
          error = AppServerBridge.getException(context);
        }
        instrumenter().end(context, request, response, error);
      }
    }
  }
}
