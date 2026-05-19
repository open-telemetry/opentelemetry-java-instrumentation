/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;

class ConstructorOnlyAddingSpanAttributes {

  @AddingSpanAttributes
  ConstructorOnlyAddingSpanAttributes(
      @SpanAttribute String implicitName,
      @SpanAttribute("explicitName") String parameter,
      @SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {}
}
