/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Registry of {@link AsyncSpanEndStrategy} implementations for tracing the asynchronous operations
 * represented by the return type of a traced method.
 */
public class AsyncSpanEndStrategies {
  private static final AsyncSpanEndStrategies instance = new AsyncSpanEndStrategies();

  public static AsyncSpanEndStrategies getInstance() {
    return instance;
  }

  private final List<AsyncSpanEndStrategy> strategies = new CopyOnWriteArrayList<>();

  private AsyncSpanEndStrategies() {
    strategies.add(Jdk8AsyncSpanEndStrategy.INSTANCE);
  }

  public void registerStrategy(AsyncSpanEndStrategy strategy) {
    Objects.requireNonNull(strategy);
    strategies.add(strategy);
  }

  public void unregisterStrategy(AsyncSpanEndStrategy strategy) {
    strategies.remove(strategy);
  }

  public void unregisterStrategy(Class<? extends AsyncSpanEndStrategy> strategyClass) {
    strategies.removeIf(strategy -> strategy.getClass() == strategyClass);
  }

  @Nullable
  public AsyncSpanEndStrategy resolveStrategy(Class<?> returnType) {
    for (AsyncSpanEndStrategy strategy : strategies) {
      if (strategy.supports(returnType)) {
        return strategy;
      }
    }
    return null;
  }
}
