/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support.async;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

/** Default strategies' registry implementation that uses strong references. */
final class AsyncOperationEndStrategiesImpl extends AsyncOperationEndStrategies {
  private final List<AsyncOperationEndStrategy> strategies = new CopyOnWriteArrayList<>();

  AsyncOperationEndStrategiesImpl() {
    registerStrategy(Jdk8AsyncOperationEndStrategy.INSTANCE);
  }

  @Override
  public void registerStrategy(AsyncOperationEndStrategy strategy) {
    strategies.add(requireNonNull(strategy));
  }

  @Override
  public void unregisterStrategy(AsyncOperationEndStrategy strategy) {
    strategies.remove(strategy);
  }

  @Nullable
  @Override
  public AsyncOperationEndStrategy resolveStrategy(Class<?> returnType) {
    for (AsyncOperationEndStrategy strategy : strategies) {
      if (strategy.supports(returnType)) {
        return strategy;
      }
    }
    return null;
  }
}
