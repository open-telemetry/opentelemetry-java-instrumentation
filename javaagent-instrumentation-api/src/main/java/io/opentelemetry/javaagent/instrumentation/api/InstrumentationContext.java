/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

/** Instrumentation Context API. */
public class InstrumentationContext {
  private InstrumentationContext() {}

  /**
   * Find a {@link ContextStore} instance for given key class and context class.
   *
   * <p>Conceptually this can be thought of as a map lookup to fetch a second level map given
   * keyClass.
   *
   * <p>In reality, the <em>calls</em> to this method are re-written to something more performant
   * while injecting advice into a method.
   *
   * <p>When this method is called from outside of an Advice class it can only access {@link
   * ContextStore} when it is already created. For this {@link ContextStore} either needs to be
   * registered in {@code
   * io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule#registerMuzzleContextStoreClasses(InstrumentationContextBuilder)}
   * or be used in an Advice or Helper class which automatically adds it to {@code
   * InstrumentationModule#registerMuzzleContextStoreClasses(InstrumentationContextBuilder)}.
   *
   * @param keyClass The key class context is attached to.
   * @param contextClass The context class attached to the user class.
   * @param <K> key class
   * @param <C> context class
   * @return The instance of context store for given arguments.
   */
  public static <Q extends K, K, C> ContextStore<Q, C> get(
      Class<K> keyClass, Class<C> contextClass) {
    if (contextStoreSupplier == null) {
      throw new IllegalStateException("Context store supplier not set");
    }
    return contextStoreSupplier.get(keyClass, contextClass);
  }

  public interface ContextStoreSupplier<Q extends K, K, C> {
    ContextStore<Q, C> get(Class<K> keyClass, Class<C> contextClass);
  }

  private static volatile ContextStoreSupplier contextStoreSupplier;

  /**
   * Sets the {@link ContextStoreSupplier} to execute when instrumentation needs to access {@link
   * ContextStore}. This is called from the agent startup, instrumentation must not call this.
   */
  public static void internalSetContextStoreSupplier(ContextStoreSupplier contextStoreSupplier) {
    if (InstrumentationContext.contextStoreSupplier != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    InstrumentationContext.contextStoreSupplier = contextStoreSupplier;
  }
}
