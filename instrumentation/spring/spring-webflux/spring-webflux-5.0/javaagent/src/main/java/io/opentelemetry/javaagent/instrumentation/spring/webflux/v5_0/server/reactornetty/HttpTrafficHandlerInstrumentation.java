/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.reactornetty;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContext;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContexts;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// used since reactor-netty-0.8
public class HttpTrafficHandlerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.netty.http.server.HttpTrafficHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("run").and(takesNoArguments()),
        HttpTrafficHandlerInstrumentation.class.getName() + "$RunAdvice");
  }

  @SuppressWarnings("unused")
  public static class RunAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(
        @Advice.FieldValue("ctx") ChannelHandlerContext channelHandlerContext) {
      // set context to the first unprocessed request
      ServerContext serverContext = ServerContexts.peekFirst(channelHandlerContext.channel());
      if (serverContext != null) {
        return serverContext.context().makeCurrent();
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
