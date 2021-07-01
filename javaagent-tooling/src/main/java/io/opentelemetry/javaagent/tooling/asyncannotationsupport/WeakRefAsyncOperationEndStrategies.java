/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.asyncannotationsupport;

import io.opentelemetry.instrumentation.api.asyncannotationsupport.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.api.asyncannotationsupport.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.asyncannotationsupport.Jdk8AsyncOperationEndStrategy;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.ListIterator;
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
    ListIterator<WeakReference<AsyncOperationEndStrategy>> it = strategies.listIterator();
    while (it.hasNext()) {
      AsyncOperationEndStrategy s = it.next().get();
      if (s == null || s == strategy) {
        it.remove();
        break;
      }
    }
  }

  @Nullable
  @Override
  public AsyncOperationEndStrategy resolveStrategy(Class<?> returnType) {
    ListIterator<WeakReference<AsyncOperationEndStrategy>> it = strategies.listIterator();
    while (it.hasNext()) {
      AsyncOperationEndStrategy s = it.next().get();
      if (s == null) {
        it.remove();
      } else if (s.supports(returnType)) {
        return s;
      }
    }
    return null;
  }
}
