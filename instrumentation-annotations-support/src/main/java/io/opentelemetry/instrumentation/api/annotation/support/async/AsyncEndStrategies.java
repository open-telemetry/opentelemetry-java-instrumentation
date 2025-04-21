/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support.async;

import javax.annotation.Nullable;

/** A global registry of {@link AsyncEndStrategy} implementations. */
public abstract class AsyncEndStrategies {
  private static volatile AsyncEndStrategies instance;

  /**
   * Sets the actual strategies' registry implementation. The javaagent uses weak references to make
   * unloading strategy classes possible.
   *
   * <p>This is supposed to be only called by the javaagent. <b>Instrumentation must not call
   * this.</b>
   */
  public static void internalSetStrategiesStorage(AsyncEndStrategies strategies) {
    instance = strategies;
  }

  /** Obtain instance of the async strategy registry. */
  public static AsyncEndStrategies instance() {
    if (instance == null) {
      instance = new AsyncEndStrategiesImpl();
    }
    return instance;
  }

  /** Add the passed {@code strategy} to the registry. */
  public abstract void registerStrategy(AsyncEndStrategy strategy);

  /** Remove the passed {@code strategy} from the registry. */
  public abstract void unregisterStrategy(AsyncEndStrategy strategy);

  /**
   * Returns an {@link AsyncEndStrategy} that is able to compose over {@code returnType}, or {@code
   * null} if passed type is not supported by any of the strategies stored in this registry.
   */
  @Nullable
  public abstract AsyncEndStrategy resolveStrategy(Class<?> returnType);
}
