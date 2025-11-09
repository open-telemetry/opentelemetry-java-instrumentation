/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.FUTURE_OPERATION;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.Future;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.Operation;

public class SetOperationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "net.spy.memcached.internal.OperationFuture", "net.spy.memcached.internal.GetFuture");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("setOperation")).and(takesArguments(1)),
        this.getClass().getName() + "$SetOperationAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetOperationAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Object future, @Advice.Argument(0) Operation operation) {

      if (future instanceof OperationFuture || future instanceof GetFuture) {
        FUTURE_OPERATION.set((Future<?>) future, operation);
      }
    }
  }
}
