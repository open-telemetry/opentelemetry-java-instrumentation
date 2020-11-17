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
   * <p>This method must only be called within an Advice class.
   *
   * @param keyClass The key class context is attached to.
   * @param contextClass The context class attached to the user class.
   * @param <K> key class
   * @param <C> context class
   * @return The instance of context store for given arguments.
   */
  public static <K, C> ContextStore<K, C> get(Class<K> keyClass, Class<C> contextClass) {
    throw new RuntimeException(
        "Calls to this method will be rewritten by Instrumentation Context Provider (e.g. FieldBackedProvider)");
  }
}
