/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;

/**
 * This interface allows registering {@link ContextStore} class pairs.
 *
 * <p>This interface should not be implemented by the javaagent extension developer - the javaagent
 * will provide the implementation of all transformations described here.
 */
public interface InstrumentationContextBuilder {

  /**
   * Register the association between the {@code keyClassName} and the {@code contextClassName}.
   * Class pairs registered using this method will be available as {@link ContextStore}s in the
   * runtime; obtainable by calling {@link InstrumentationContext#get(Class, Class)}.
   *
   * @param keyClassName The name of the class that an instance of class named {@code
   *     contextClassName} will be attached to.
   * @param contextClassName The instrumentation context class name.
   * @return {@code this}.
   * @see InstrumentationContext
   * @see ContextStore
   */
  InstrumentationContextBuilder register(String keyClassName, String contextClassName);
}
