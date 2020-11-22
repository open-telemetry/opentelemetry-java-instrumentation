/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.extension.annotations.WithSpan;

public class TracedWithSpan {

  @WithSpan
  public String otel() {
    Span.current().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan("manualName")
  public String namedOtel() {
    Span.current().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan
  public String ignored() {
    Span.current().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan(kind = Kind.PRODUCER)
  public String oneOfAKind() {
    Span.current().setAttribute("providerAttr", "Otel");
    return "hello!";
  }
}
