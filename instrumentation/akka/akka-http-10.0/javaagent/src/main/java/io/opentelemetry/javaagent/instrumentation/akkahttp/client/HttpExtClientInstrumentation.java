/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.client.AkkaHttpClientSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.client.AkkaHttpClientSingletons.setter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.actor.ActorSystem;
import akka.http.scaladsl.HttpExt;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.Future;

public class HttpExtClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.HttpExt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // singleRequestImpl is only present in 10.1.x
    transformer.applyAdviceToMethod(
        namedOneOf("singleRequest", "singleRequestImpl")
            .and(takesArgument(0, named("akka.http.scaladsl.model.HttpRequest"))),
        this.getClass().getName() + "$SingleRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class SingleRequestAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope start(HttpRequest request) {
        Context parentContext = currentContext();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        // Making context current is required for header context propagation to work as expected
        // because it implicitly relies on the current context when injecting headers.
        Scope scope = context.makeCurrent();
        return new AdviceScope(context, scope);
      }

      public HttpRequest injectHeaders(HttpRequest request) {
        // Request is immutable, so we have to assign a new value once we update headers
        return setter().inject(request);
      }

      public Future<HttpResponse> end(
          @Nullable ActorSystem actorSystem,
          HttpRequest request,
          @Nullable Future<HttpResponse> responseFuture,
          @Nullable Throwable throwable) {

        scope.close();
        if (actorSystem != null) {
          if (throwable == null) {
            responseFuture.onComplete(
                new OnCompleteHandler(context, request), actorSystem.dispatcher());
            return FutureWrapper.wrap(responseFuture, actorSystem.dispatcher(), currentContext());
          } else {
            instrumenter().end(context, request, null, throwable);
          }
        }
        return responseFuture;
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(@Advice.Argument(0) HttpRequest request) {

      /*
      Versions 10.0 and 10.1 have slightly different structure that is hard to distinguish so here
      we cast 'wider net' and avoid instrumenting twice.
      In the future we may want to separate these, but since lots of code is reused we would need to come up
      with way of continuing to reusing it.
       */
      AdviceScope adviceScope = AdviceScope.start(request);
      return new Object[] {
        adviceScope, adviceScope == null ? request : adviceScope.injectHeaders(request)
      };
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Future<HttpResponse> methodExit(
        @Advice.This HttpExt thiz,
        @Advice.Return @Nullable Future<HttpResponse> responseFuture,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope == null) {
        return responseFuture;
      }
      ActorSystem actorSystem = AkkaHttpClientUtil.getActorSystem(thiz);
      return adviceScope.end(actorSystem, (HttpRequest) enterResult[1], responseFuture, throwable);
    }
  }
}
