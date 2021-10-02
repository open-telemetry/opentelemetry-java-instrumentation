/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.instrumentation.api.field.VirtualField;

/**
 * This interface allows registering {@link VirtualField} class pairs.
 *
 * <p>This interface should not be implemented by the javaagent extension developer - the javaagent
 * will provide the implementation of all transformations described here.
 */
// TODO: we should rename the class so that it doesn't mention "Context", but "VirtualField"
// instead; probably once this class is hidden somewhere in the muzzle codegen contract
public interface InstrumentationContextBuilder {

  /**
   * Register the association between the {@code keyClassName} and the {@code contextClassName}.
   * Class pairs registered using this method will be available as {@link VirtualField}s in the
   * runtime; obtainable by calling {@link VirtualField#find(Class, Class)}.
   *
   * @param keyClassName The name of the class that an instance of class named {@code
   *     contextClassName} will be attached to.
   * @param contextClassName The instrumentation context class name.
   * @return {@code this}.
   * @see VirtualField
   */
  InstrumentationContextBuilder register(String keyClassName, String contextClassName);
}
