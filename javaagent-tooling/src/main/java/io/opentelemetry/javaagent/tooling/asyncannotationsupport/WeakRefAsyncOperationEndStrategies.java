/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.asyncannotationsupport;

import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.annotation.support.async.Jdk8AsyncOperationEndStrategy;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class WeakRefAsyncOperationEndStrategies extends AsyncOperationEndStrategies {

  /**
   * Use the weak reference strategy in the agent. This will prevent leaking reference to
   * strategies' classloaders, in case applications get undeployed (and all their classes unloaded).
   */
  public static void initialize() {
    AsyncOperationEndStrategies.internalSetStrategiesStorage(
        new WeakRefAsyncOperationEndStrategies());
  }

  private final List<WeakReference<AsyncOperationEndStrategy>> strategies =
      new CopyOnWriteArrayList<>();

  private WeakRefAsyncOperationEndStrategies() {
    registerStrategy(Jdk8AsyncOperationEndStrategy.INSTANCE);
  }

  @Override
  public void registerStrategy(AsyncOperationEndStrategy strategy) {
    strategies.add(new WeakReference<>(strategy));
  }

  @Override
  public void unregisterStrategy(AsyncOperationEndStrategy strategy) {
    strategies.removeIf(
        ref -> {
          AsyncOperationEndStrategy s = ref.get();
          return s == null || s == strategy;
        });
  }

  @Nullable
  @Override
  public AsyncOperationEndStrategy resolveStrategy(Class<?> returnType) {
    boolean purgeCollectedWeakReferences = false;
    try {
      for (WeakReference<AsyncOperationEndStrategy> ref : strategies) {
        AsyncOperationEndStrategy s = ref.get();
        if (s == null) {
          purgeCollectedWeakReferences = true;
        } else if (s.supports(returnType)) {
          return s;
        }
      }
      return null;
    } finally {
      if (purgeCollectedWeakReferences) {
        strategies.removeIf(ref -> ref.get() == null);
      }
    }
  }
}
