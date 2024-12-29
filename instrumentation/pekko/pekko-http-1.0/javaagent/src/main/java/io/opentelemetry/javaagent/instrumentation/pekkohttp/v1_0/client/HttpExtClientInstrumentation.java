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
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
    @Advice.AssignReturned.ToArguments(@ToArgument(value = 0, index = 0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(@Advice.Argument(value = 0) HttpRequest request) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return new Object[] {request, null, null};
      }

      Context context = instrumenter().start(parentContext, request);
      Scope scope = context.makeCurrent();

      // Request is immutable, so we have to assign new value once we update headers
      HttpRequest modifiedRequest = setter().inject(request);

      // Using array return form allows to provide at the same time
      // - an argument value to override
      // - propagate state from enter to exit advice
      //
      // As an array is already allocated we avoid creating another object
      // by storing context and scope directly into the array.
      return new Object[] {modifiedRequest, context, scope};
    }

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Future<HttpResponse> methodExit(
        @Advice.Argument(0) HttpRequest request,
        @Advice.This HttpExt thiz,
        @Advice.Return Future<HttpResponse> responseFuture,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enter) {

      if (!(enter[1] instanceof Context) || !(enter[2] instanceof Scope)) {
        return null;
      }
      ((Scope) enter[2]).close();
      Context context = (Context) enter[1];

      if (throwable != null) {
        instrumenter().end(context, request, null, throwable);
        return responseFuture;
      }
      if (responseFuture == null) {
        return null;
      }
      responseFuture.onComplete(
          new OnCompleteHandler(context, request), thiz.system().dispatcher());

      return FutureWrapper.wrap(responseFuture, thiz.system().dispatcher(), currentContext());
    }
  }
}
