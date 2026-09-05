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
import scala.Option;
import scala.Tuple2;
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
        getClass().getName() + "$AddAdvice");
    transformer.applyAdviceToMethod(
        named("remove").and(takesArguments(1)), getClass().getName() + "$RemoveAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This HashMap<?, ?> map,
        @Advice.Argument(0) Tuple2<?, ?> entry,
        @Advice.Local("endpoint") @Nullable String endpoint,
        @Advice.Local("present") boolean present,
        @Advice.Local("captured") boolean captured) {
      RediscalaServerTargets.MutablePoolState state =
          RediscalaServerTargets.getMutablePoolState(map);
      if (state == null || !state.isAvailable()) {
        return;
      }

      Object key = entry._1();
      endpoint = RediscalaServerTargets.endpoint(key);
      if (endpoint == null) {
        return;
      }

      present = RediscalaServerTargets.contains(map, key);
      captured = true;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This HashMap<?, ?> map,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Local("endpoint") @Nullable String endpoint,
        @Advice.Local("present") boolean present,
        @Advice.Local("captured") boolean captured) {
      RediscalaServerTargets.MutablePoolState state =
          RediscalaServerTargets.getMutablePoolState(map);
      if (state == null || !state.isAvailable()) {
        return;
      }
      if (throwable != null || !captured) {
        state.markUnavailable();
        return;
      }
      if (!present) {
        state.add(endpoint);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This HashMap<?, ?> map,
        @Advice.Argument(0) Object key,
        @Advice.Local("endpoint") @Nullable String endpoint,
        @Advice.Local("captured") boolean captured) {
      RediscalaServerTargets.MutablePoolState state =
          RediscalaServerTargets.getMutablePoolState(map);
      if (state == null || !state.isAvailable()) {
        return;
      }

      endpoint = RediscalaServerTargets.endpoint(key);
      if (endpoint != null) {
        captured = true;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This HashMap<?, ?> map,
        @Advice.Return @Nullable Option<?> result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Local("endpoint") @Nullable String endpoint,
        @Advice.Local("captured") boolean captured) {
      RediscalaServerTargets.MutablePoolState state =
          RediscalaServerTargets.getMutablePoolState(map);
      if (state == null || !state.isAvailable()) {
        return;
      }
      if (throwable != null || !captured || result == null) {
        state.markUnavailable();
        return;
      }
      if (result.isDefined()) {
        state.remove(endpoint);
      }
    }
  }
}
