/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of {@link MethodSpanStrategy} implementations for tracing the asynchronous operations
 * represented by the return type of a traced method.
 */
public class MethodSpanStrategies {
  private static final MethodSpanStrategies instance = new MethodSpanStrategies();

  public static MethodSpanStrategies getInstance() {
    return instance;
  }

  private final List<MethodSpanStrategy> strategies = new CopyOnWriteArrayList<>();

  private MethodSpanStrategies() {
    strategies.add(Jdk8MethodStrategy.INSTANCE);
  }

  public void registerStrategy(MethodSpanStrategy strategy) {
    Objects.requireNonNull(strategy);
    strategies.add(strategy);
  }

  public MethodSpanStrategy resolveStrategy(Class<?> returnType) {
    for (MethodSpanStrategy strategy : strategies) {
      if (strategy.supports(returnType)) {
        return strategy;
      }
    }
    return MethodSpanStrategy.synchronous();
  }
}
