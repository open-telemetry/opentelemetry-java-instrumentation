/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.v12_0.Jetty12Singletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

class Jetty12ServerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.server.Server");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handle")
            .and(takesArgument(0, named("org.eclipse.jetty.server.Request")))
            .and(takesArgument(1, named("org.eclipse.jetty.server.Response")))
            .and(takesArgument(2, named("org.eclipse.jetty.util.Callback")))
            .and(isPublic()),
        this.getClass().getName() + "$HandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object source,
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) Response response,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!helper().shouldStart(parentContext, request)) {
        return;
      }

      context = helper().start(parentContext, request, response);
      scope = context.makeCurrent();

      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(context, response, Jetty12ResponseMutator.INSTANCE);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) Response response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      if (throwable != null) {
        helper().end(context, request, response, throwable);
      }
    }
  }
}
