/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9.AsyncHttpClientSingletons.ASYNC_HANDLER_DATA;
import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9.AsyncHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Request;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.ning.http.client.AsyncHttpClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("executeRequest")
            .and(takesArgument(0, named("com.ning.http.client.Request")))
            .and(takesArgument(1, named("com.ning.http.client.AsyncHandler")))
            .and(isPublic()),
        this.getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(
        @Advice.Argument(0) Request request, @Advice.Argument(1) AsyncHandler<?> handler) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, request);
      ASYNC_HANDLER_DATA.set(handler, AsyncHandlerData.create(parentContext, context, request));
      return context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
      // span ended in OnCompletedAdvice or OnThrowableAdvice
    }
  }
}
