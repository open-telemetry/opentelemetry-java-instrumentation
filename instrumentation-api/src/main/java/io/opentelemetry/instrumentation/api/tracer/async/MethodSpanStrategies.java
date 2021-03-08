/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MethodSpanStrategies {
  private static final ConcurrentMap<Class<?>, MethodSpanStrategy> strategies =
      new ConcurrentHashMap<>();

  static {
    registerStrategy(CompletionStage.class, MethodSpanStrategy.forCompletionStage());
    registerStrategy(CompletableFuture.class, MethodSpanStrategy.forCompletableFuture());
  }

  public static MethodSpanStrategy resolveStrategy(Method method) {
    Class<?> returnType = method.getReturnType();
    return strategies.getOrDefault(returnType, MethodSpanStrategy.synchronous());
  }

  public static void registerStrategy(Class<?> returnType, MethodSpanStrategy strategy) {
    strategies.put(returnType, strategy);
  }

  private MethodSpanStrategies() {}
}
