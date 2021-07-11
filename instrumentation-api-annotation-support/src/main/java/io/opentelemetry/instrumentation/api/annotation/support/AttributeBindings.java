/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.instrumentation.api.tracer.AttributeSetter;

/** Represents the bindings of method parameters to attributes of a traced method. */
public interface AttributeBindings {

  /**
   * Indicates that the traced method has no parameters bound to attributes.
   *
   * @return {@code true} if the traced method has no bound parameters; otherwise {@code false}
   */
  boolean isEmpty();

  /**
   * Applies the values of the method arguments as attributes on the span for the traced method.
   *
   * @param setter the {@link AttributeSetter} for setting the attribute on the span
   * @param args the method arguments
   */
  void apply(AttributeSetter setter, Object[] args);
}
