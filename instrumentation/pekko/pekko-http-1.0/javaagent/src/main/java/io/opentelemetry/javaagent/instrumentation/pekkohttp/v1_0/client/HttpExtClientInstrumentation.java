/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client.PekkoHttpClientSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client.PekkoHttpClientSingletons.setter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.scaladsl.HttpExt;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import scala.concurrent.Future;

public class HttpExtClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.scaladsl.HttpExt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("singleRequest")
            .and(takesArgument(0, named("org.apache.pekko.http.scaladsl.model.HttpRequest"))),
        this.getClass().getName() + "$SingleRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class SingleRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0, readOnly = false) HttpRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
      // Request is immutable, so we have to assign new value once we update headers
      request = setter().inject(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) HttpRequest request,
        @Advice.This HttpExt thiz,
        @Advice.Return(readOnly = false) Future<HttpResponse> responseFuture,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      if (throwable == null) {
        responseFuture.onComplete(
            new OnCompleteHandler(context, request), thiz.system().dispatcher());
      } else {
        instrumenter().end(context, request, null, throwable);
      }
      if (responseFuture != null) {
        responseFuture =
            FutureWrapper.wrap(responseFuture, thiz.system().dispatcher(), currentContext());
      }
    }
  }
}
