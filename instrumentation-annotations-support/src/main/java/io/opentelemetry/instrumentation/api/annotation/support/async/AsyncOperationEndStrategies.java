/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support.async;

import javax.annotation.Nullable;

/** A global registry of {@link AsyncOperationEndStrategy} implementations. */
public abstract class AsyncOperationEndStrategies {
  private static volatile AsyncOperationEndStrategies instance;

  /**
   * Sets the actual strategies' registry implementation. The javaagent uses weak references to make
   * unloading strategy classes possible.
   *
   * <p>This is supposed to be only called by the javaagent. <b>Instrumentation must not call
   * this.</b>
   */
  public static void internalSetStrategiesStorage(AsyncOperationEndStrategies strategies) {
    instance = strategies;
  }

  /** Obtain instance of the async strategy registry. */
  public static AsyncOperationEndStrategies instance() {
    if (instance == null) {
      instance = new AsyncOperationEndStrategiesImpl();
    }
    return instance;
  }

  /** Add the passed {@code strategy} to the registry. */
  public abstract void registerStrategy(AsyncOperationEndStrategy strategy);

  /** Remove the passed {@code strategy} from the registry. */
  public abstract void unregisterStrategy(AsyncOperationEndStrategy strategy);

  /**
   * Returns an {@link AsyncOperationEndStrategy} that is able to compose over {@code returnType},
   * or {@code null} if passed type is not supported by any of the strategies stored in this
   * registry.
   */
  @Nullable
  public abstract AsyncOperationEndStrategy resolveStrategy(Class<?> returnType);
}
