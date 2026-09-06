/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.collection.mutable.HashMap;

class RediscalaHashMapInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("redis.RedisClientMutablePool");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("scala.collection.mutable.HashMap");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("$plus$eq", "addOne").and(takesArgument(0, named("scala.Tuple2"))),
        getClass().getName() + "$MutationAdvice");
    transformer.applyAdviceToMethod(
        named("remove").and(takesArguments(1)), getClass().getName() + "$MutationAdvice");
  }

  @SuppressWarnings("unused")
  public static class MutationAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This HashMap<?, ?> map, @Advice.Thrown @Nullable Throwable throwable) {
      RediscalaServerTargets.MutablePoolState state =
          RediscalaServerTargets.getMutablePoolState(map);
      if (state == null) {
        return;
      }
      if (throwable != null) {
        state.markUnavailable();
        return;
      }
      state.refresh(map);
    }
  }
}
