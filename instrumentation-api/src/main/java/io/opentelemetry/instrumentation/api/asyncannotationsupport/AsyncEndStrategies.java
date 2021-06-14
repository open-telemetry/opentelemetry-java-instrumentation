/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.asyncannotationsupport;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A global registry of {@link AsyncOperationEndStrategy} implementations. */
public final class AsyncEndStrategies {
  private static final List<AsyncOperationEndStrategy> STRATEGIES = new CopyOnWriteArrayList<>();

  static {
    STRATEGIES.add(Jdk8AsyncOperationEndStrategy.INSTANCE);
  }

  /** Add the passed {@code strategy} to the registry. */
  public static void registerStrategy(AsyncOperationEndStrategy strategy) {
    STRATEGIES.add(requireNonNull(strategy));
  }

  /** Remove the passed {@code strategy} from the registry. */
  public static void unregisterStrategy(AsyncOperationEndStrategy strategy) {
    STRATEGIES.remove(strategy);
  }

  /** Remove all strategies of type {@code strategyClass} from the registry. */
  public static void unregisterStrategy(Class<? extends AsyncOperationEndStrategy> strategyClass) {
    STRATEGIES.removeIf(strategy -> strategy.getClass() == strategyClass);
  }

  /**
   * Returns an {@link AsyncOperationEndStrategy} that is able to compose over {@code returnType},
   * or {@code null} if passed type is not supported by any of the strategies stored in this
   * registry.
   */
  @Nullable
  public static AsyncOperationEndStrategy resolveStrategy(Class<?> returnType) {
    for (AsyncOperationEndStrategy strategy : STRATEGIES) {
      if (strategy.supports(returnType)) {
        return strategy;
      }
    }
    return null;
  }
}
