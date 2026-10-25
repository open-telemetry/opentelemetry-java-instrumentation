/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Function1;
import scala.Function2;
import scala.concurrent.Future;

class ResponseFutureInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "scala.concurrent.impl.Promise$DefaultPromise",
        "akka.http.scaladsl.util.FastFuture$ErrorFuture");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Scala 2.11/2.12 delegate combinators to onComplete. Scala 2.13 overrides them, and Akka's
    // ErrorFuture implements transforms inline, so intercept their callback arguments as well.
    transformer.applyAdviceToMethod(
        namedOneOf(
                "onComplete",
                "foreach",
                "map",
                "flatMap",
                "filter",
                "collect",
                "recover",
                "recoverWith",
                "transform",
                "transformWith")
            .and(takesArguments(2))
            .and(takesArgument(1, named("scala.concurrent.ExecutionContext"))),
        getClass().getName() + "$CallbackAdvice");
    transformer.applyAdviceToMethod(
        named("transform")
            .and(takesArgument(1, named("scala.Function1")))
            .and(takesArgument(2, named("scala.concurrent.ExecutionContext"))),
        getClass().getName() + "$TransformAdvice");
    transformer.applyAdviceToMethod(
        named("zipWith")
            .and(takesArgument(1, named("scala.Function2")))
            .and(takesArgument(2, named("scala.concurrent.ExecutionContext"))),
        getClass().getName() + "$ZipAdvice");
    // These DefaultPromise combinators allocate a result promise (or return the marked receiver).
    // Do not mark FastFuture.transformWith results: they can be unrelated, shared application
    // futures.
    transformer.applyAdviceToMethod(
        isDeclaredBy(named("scala.concurrent.impl.Promise$DefaultPromise"))
            .and(
                namedOneOf(
                    "map",
                    "flatMap",
                    "filter",
                    "collect",
                    "recover",
                    "recoverWith",
                    "transform",
                    "transformWith",
                    "andThen"))
            .and(returns(named("scala.concurrent.Future"))),
        getClass().getName() + "$ResultAdvice");
    transformer.applyAdviceToMethod(
        isDeclaredBy(named("scala.concurrent.impl.Promise$DefaultPromise"))
            .and(namedOneOf("zip", "zipWith", "fallbackTo"))
            .and(takesArgument(0, named("scala.concurrent.Future"))),
        getClass().getName() + "$CombinedResultAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallbackAdvice {
    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Function1<?, ?> onEnter(
        @Advice.This Future<?> future, @Advice.Argument(0) Function1<?, ?> callback) {
      return ResponseFutureCallbacks.wrap(future, callback);
    }
  }

  @SuppressWarnings("unused")
  public static class TransformAdvice {
    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 1, index = 1)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This Future<?> future,
        @Advice.Argument(0) Function1<?, ?> success,
        @Advice.Argument(1) Function1<?, ?> failure) {
      return new Object[] {
        ResponseFutureCallbacks.wrap(future, success), ResponseFutureCallbacks.wrap(future, failure)
      };
    }
  }

  @SuppressWarnings("unused")
  public static class ZipAdvice {
    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Function2<?, ?, ?> onEnter(
        @Advice.This Future<?> future, @Advice.Argument(1) Function2<?, ?, ?> callback) {
      return ResponseFutureCallbacks.wrap(future, callback);
    }
  }

  @SuppressWarnings("unused")
  public static class ResultAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Future<?> source, @Advice.Return Future<?> result) {
      ResponseFutureCallbacks.inherit(source, result);
    }
  }

  @SuppressWarnings("unused")
  public static class CombinedResultAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Future<?> source,
        @Advice.Argument(0) Future<?> other,
        @Advice.Return Future<?> result) {
      if (result != other) {
        ResponseFutureCallbacks.inherit(source, result);
      }
    }
  }
}
