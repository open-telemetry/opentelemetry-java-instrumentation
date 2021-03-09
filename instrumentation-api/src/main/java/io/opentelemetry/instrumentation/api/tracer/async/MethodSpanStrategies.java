/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Registry of {@link MethodSpanStrategy} implementations for tracing the asynchronous operations
 * represented by the return type of a traced method.
 */
public class MethodSpanStrategies {
  private static final MethodSpanStrategies instance;

  static {
    Map<Class<?>, MethodSpanStrategy> strategies = new HashMap<>();
    strategies.put(CompletionStage.class, MethodSpanStrategy.forCompletionStage());
    strategies.put(CompletableFuture.class, MethodSpanStrategy.forCompletableFuture());
    instance = new MethodSpanStrategies(strategies);
  }

  public static MethodSpanStrategies getInstance() {
    return instance;
  }

  private final Map<Class<?>, MethodSpanStrategy> strategies;

  private MethodSpanStrategies(Map<Class<?>, MethodSpanStrategy> strategies) {
    this.strategies = strategies;
  }

  public void registerStrategy(Class<?> returnType, MethodSpanStrategy strategy) {
    strategies.put(returnType, strategy);
  }

  public MethodSpanStrategy resolveStrategy(Class<?> returnType) {
    return strategies.getOrDefault(returnType, MethodSpanStrategy.synchronous());
  }
}
