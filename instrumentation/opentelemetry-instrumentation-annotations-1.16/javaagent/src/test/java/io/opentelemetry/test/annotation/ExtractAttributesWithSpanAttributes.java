/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.WithSpanAttributes;

public class ExtractAttributesWithSpanAttributes {

  @WithSpanAttributes
  public String withSpanAttributes(
      @SpanAttribute String implicitName,
      @SpanAttribute("explicitName") String parameter,
      @SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {

    return "hello!";
  }

  @WithSpanAttributes
  public String withSpanAttributesParent(
      @SpanAttribute String implicitName,
      @SpanAttribute("explicitName") String parameter,
      @SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {

    return withSpanAttributes("foo", "bar", null, "baz");
  }

  @WithSpan
  @WithSpanAttributes
  public String withSpanTakesPrecedence(
      @SpanAttribute String implicitName,
      @SpanAttribute("explicitName") String parameter,
      @SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {

    return "hello!";
  }
}
