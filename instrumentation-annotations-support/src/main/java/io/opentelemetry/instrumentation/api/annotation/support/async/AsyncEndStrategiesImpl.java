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
final class AsyncEndStrategiesImpl extends AsyncEndStrategies {
  private final List<AsyncEndStrategy> strategies = new CopyOnWriteArrayList<>();

  AsyncEndStrategiesImpl() {
    registerStrategy(Jdk8AsyncEndStrategy.INSTANCE);
  }

  @Override
  public void registerStrategy(AsyncEndStrategy strategy) {
    strategies.add(requireNonNull(strategy));
  }

  @Override
  public void unregisterStrategy(AsyncEndStrategy strategy) {
    strategies.remove(strategy);
  }

  @Nullable
  @Override
  public AsyncEndStrategy resolveStrategy(Class<?> returnType) {
    for (AsyncEndStrategy strategy : strategies) {
      if (strategy.supports(returnType)) {
        return strategy;
      }
    }
    return null;
  }
}
