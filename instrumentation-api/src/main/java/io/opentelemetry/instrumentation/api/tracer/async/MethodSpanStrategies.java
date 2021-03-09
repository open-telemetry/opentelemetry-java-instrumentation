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

/**
 * Registry of {@link MethodSpanStrategy} implementations for tracing the asynchronous operations
 * represented by the return type of a traced method.
 */
public class MethodSpanStrategies {
  private static final ConcurrentMap<Class<?>, MethodSpanStrategy> strategies =
      new ConcurrentHashMap<>();

  static {
    registerStrategy(CompletionStage.class, MethodSpanStrategy.forCompletionStage());
    registerStrategy(CompletableFuture.class, MethodSpanStrategy.forCompletableFuture());
  }

  public static MethodSpanStrategy resolveStrategy(Method method) {
    return resolveStrategy(method.getReturnType());
  }

  public static MethodSpanStrategy resolveStrategy(Class<?> returnType) {
    return strategies.getOrDefault(returnType, MethodSpanStrategy.synchronous());
  }

  public static void registerStrategy(Class<?> returnType, MethodSpanStrategy strategy) {
    strategies.put(returnType, strategy);
  }

  private MethodSpanStrategies() {}
}
