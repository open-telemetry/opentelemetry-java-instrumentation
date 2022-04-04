/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.misc.RPromise;

public class RedisCommandAsyncServiceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.command.CommandAsyncService");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // used before 3.16.8
    transformer.applyAdviceToMethod(
        named("async").and(takesArgument(5, named("org.redisson.misc.RPromise"))),
        this.getClass().getName() + "$WrapPromiseAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapPromiseAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 5, readOnly = false) RPromise<?> promise) {
      promise = RedissonPromiseWrapper.wrapContext(promise);
    }
  }
}
