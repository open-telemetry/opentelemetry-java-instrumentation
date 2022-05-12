/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.instrumentation.api.util.VirtualField;

/**
 * This interface allows registering {@link VirtualField} class pairs.
 *
 * <p>This interface should not be implemented by the javaagent extension developer - the javaagent
 * will provide the implementation of all transformations described here.
 */
public interface VirtualFieldMappingsBuilder {

  /**
   * Register the association between the {@code typeName} and the {@code fieldTypeName}. Class
   * pairs registered using this method will be available as {@link VirtualField}s in the runtime;
   * obtainable by calling {@link VirtualField#find(Class, Class)}.
   *
   * @param typeName The name of the type that will contain the virtual field of type named {@code
   *     fieldTypeName}.
   * @param fieldTypeName The name of the field type that will be added to {@code type}.
   * @return {@code this}.
   * @see VirtualField
   */
  VirtualFieldMappingsBuilder register(String typeName, String fieldTypeName);
}
