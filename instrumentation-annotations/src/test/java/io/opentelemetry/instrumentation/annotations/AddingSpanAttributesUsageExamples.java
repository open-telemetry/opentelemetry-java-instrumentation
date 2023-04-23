/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations;

import io.opentelemetry.api.trace.Span;

/**
 * This class is not a classical test. It's just a demonstration of possible usages of {@link
 * AddingSpanAttributes} annotation together with some explanations. The goal of this class is to
 * serve as an early detection system for inconvenient API and unintended API breakage.
 */
@SuppressWarnings("unused")
public class AddingSpanAttributesUsageExamples {

  /**
   * The current {@link Span} will be updated to contain the annotated method parameters as
   * attributes.
   *
   * @param attribute1 A span attribute with the default name of {@code attribute1}.
   * @param value A span attribute with the name "attribute2".
   */
  @AddingSpanAttributes
  public void attributes(
      @SpanAttribute String attribute1, @SpanAttribute("attribute2") long value) {}
}
