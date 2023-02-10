/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithCurrentSpan;
import io.opentelemetry.instrumentation.annotations.WithSpan;

public class SpanAttributesWithCurrentSpan {

  @WithCurrentSpan
  public String withCurrentSpanAttributes(
      @SpanAttribute String implicitName,
      @SpanAttribute("explicitName") String parameter,
      @SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {

    return "hello!";
  }

  @WithSpan
  @WithCurrentSpan
  public String withSpanAttributes(
      @SpanAttribute String implicitName,
      @SpanAttribute("explicitName") String parameter,
      @SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {

    return "hello!";
  }
}
