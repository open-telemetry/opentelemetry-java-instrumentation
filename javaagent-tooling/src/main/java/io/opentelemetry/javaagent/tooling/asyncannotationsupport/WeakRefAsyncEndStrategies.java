/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.asyncannotationsupport;

import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncEndStrategies;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncEndStrategy;
import io.opentelemetry.instrumentation.api.annotation.support.async.Jdk8AsyncEndStrategy;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

public final class WeakRefAsyncEndStrategies extends AsyncEndStrategies {

  /**
   * Use the weak reference strategy in the agent. This will prevent leaking reference to
   * strategies' classloaders, in case applications get undeployed (and all their classes unloaded).
   */
  public static void initialize() {
    AsyncEndStrategies.internalSetStrategiesStorage(new WeakRefAsyncEndStrategies());
  }

  private final List<WeakReference<AsyncEndStrategy>> strategies = new CopyOnWriteArrayList<>();

  private WeakRefAsyncEndStrategies() {
    registerStrategy(Jdk8AsyncEndStrategy.INSTANCE);
  }

  @Override
  public void registerStrategy(AsyncEndStrategy strategy) {
    strategies.add(new WeakReference<>(strategy));
  }

  @Override
  public void unregisterStrategy(AsyncEndStrategy strategy) {
    strategies.removeIf(
        ref -> {
          AsyncEndStrategy s = ref.get();
          return s == null || s == strategy;
        });
  }

  @Nullable
  @Override
  public AsyncEndStrategy resolveStrategy(Class<?> returnType) {
    boolean purgeCollectedWeakReferences = false;
    try {
      for (WeakReference<AsyncEndStrategy> ref : strategies) {
        AsyncEndStrategy s = ref.get();
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
