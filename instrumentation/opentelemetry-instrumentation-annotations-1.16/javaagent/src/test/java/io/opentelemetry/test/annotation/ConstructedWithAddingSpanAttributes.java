/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;

class ConstructedWithAddingSpanAttributes {

  @AddingSpanAttributes
  ConstructedWithAddingSpanAttributes(
      @SpanAttribute String implicitName,
      @SpanAttribute("explicitName") String parameter,
      @SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {}

  @AddingSpanAttributes
  void addAttributes(@SpanAttribute("methodAttribute") String methodAttribute) {}
}
