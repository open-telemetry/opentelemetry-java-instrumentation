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
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.CompletableFutureWrapper;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonErrorCapture;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.misc.RPromise;

class RedisCommandDataInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.redisson.client.protocol.CommandData",
        "org.redisson.client.protocol.CommandsData",
        "org.redisson.client.protocol.BatchCommandData");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // before 3.16.8
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("org.redisson.misc.RPromise"))),
        getClass().getName() + "$WrapPromiseAdvice");
    // since 3.16.8
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, CompletableFuture.class)),
        getClass().getName() + "$WrapCompletableFutureAdvice");
    transformer.applyAdviceToMethod(
        named("tryFailure").and(takesArgument(0, Throwable.class)),
        getClass().getName() + "$CaptureErrorAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapPromiseAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static RPromise<?> onEnter(@Advice.Argument(0) RPromise<?> promise) {
      return RedissonPromiseWrapper.wrap(promise);
    }
  }

  @SuppressWarnings("unused")
  public static class WrapCompletableFutureAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static CompletableFuture<?> onEnter(
        @Advice.Argument(0) CompletableFuture<?> completableFuture) {
      return CompletableFutureWrapper.wrap(completableFuture);
    }
  }

  @SuppressWarnings("unused")
  public static class CaptureErrorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(0) Throwable error) {
      RedissonErrorCapture.captureServerError(error);
    }
  }
}
