/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.redisson.CompletableFutureWrapper;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.misc.RPromise;

public class RedisCommandDataInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.redisson.client.protocol.CommandData", "org.redisson.client.protocol.CommandsData");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // before 3.16.8
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("org.redisson.misc.RPromise"))),
        this.getClass().getName() + "$WrapPromiseAdvice");
    // since 3.16.8
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, CompletableFuture.class)),
        this.getClass().getName() + "$WrapCompletableFutureAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapPromiseAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static RPromise<?> onEnter(@Advice.Argument(0) RPromise<?> promise) {
      return RedissonPromiseWrapper.wrap(promise);
    }
  }

  @SuppressWarnings("unused")
  public static class WrapCompletableFutureAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static CompletableFuture<?> onEnter(
        @Advice.Argument(0) CompletableFuture<?> completableFuture) {
      return CompletableFutureWrapper.wrap(completableFuture);
    }
  }
}
