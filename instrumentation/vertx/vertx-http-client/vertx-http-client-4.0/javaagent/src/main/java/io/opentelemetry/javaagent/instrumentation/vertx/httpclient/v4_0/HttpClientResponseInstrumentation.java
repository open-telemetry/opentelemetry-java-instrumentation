/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v4_0.VertxClientSingletons.CONTEXTS;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.httpclient.common.v3_0.Contexts;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Propagate request context to HTTP response callbacks. */
class HttpClientResponseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.core.http.impl.HttpClientResponseImpl");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.http.impl.HttpClientResponseImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("handler", "endHandler", "exceptionHandler", "customFrameHandler")
            .and(takesArgument(0, named("io.vertx.core.Handler"))),
        getClass().getName() + "$HandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandlerAdvice {

    @Nullable
    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<?> wrapHandler(
        @Advice.This HttpClientResponse response,
        @Advice.Argument(0) @Nullable Handler<?> handler) {
      Contexts contexts = CONTEXTS.get(response.request());
      if (contexts == null) {
        return handler;
      }
      return HandlerWrapper.wrap(handler, contexts.parentContext);
    }
  }
}
