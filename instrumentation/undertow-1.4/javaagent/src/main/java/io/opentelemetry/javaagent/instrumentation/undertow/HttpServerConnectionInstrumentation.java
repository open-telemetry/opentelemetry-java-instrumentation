/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.undertow.UndertowSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpServerConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.undertow.server.ServerConnection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.undertow.server.ServerConnection"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getSinkConduit")
            .and(takesArgument(0, named("io.undertow.server.HttpServerExchange"))),
        this.getClass().getName() + "$ResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResponseAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static CallDepth onEnter(@Advice.Argument(0) HttpServerExchange exchange) {
      CallDepth callDepth = CallDepth.forClass(ServerConnection.class);
      if (callDepth.getAndIncrement() > 0) {
        return callDepth;
      }

      Context context = helper().getServerContext(exchange);
      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(context, exchange, UndertowHttpResponseMutator.INSTANCE);
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
