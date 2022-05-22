/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.AttributesBuilder;

/** Represents the bindings of method parameters to attributes of a traced method. */
interface AttributeBindings {

  /**
   * Indicates that the traced method has no parameters bound to attributes.
   *
   * @return {@code true} if the traced method has no bound parameters; otherwise {@code false}
   */
  boolean isEmpty();

  /**
   * Applies the values of the method arguments as attributes on the span for the traced method.
   *
   * @param target the {@link AttributesBuilder} on which to set the attribute
   * @param args the method arguments
   */
  void apply(AttributesBuilder target, Object[] args);
}
