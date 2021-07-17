/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Extractor for the attribute names for the parameters of a traced method. */
@FunctionalInterface
public interface ParameterAttributeNamesExtractor {
  /**
   * Returns an array of the names of the attributes for the parameters of the traced method. The
   * array should be the same length as the array of the method parameters. An element may be {@code
   * null} to indicate that the parameter should not be bound to an attribute. The array may also be
   * {@code null} to indicate that the method has no parameters to bind to attributes.
   *
   * @param method the traced method
   * @param parameters the method parameters
   * @return an array of the attribute names
   */
  @Nullable
  String[] extract(Method method, Parameter[] parameters);
}
