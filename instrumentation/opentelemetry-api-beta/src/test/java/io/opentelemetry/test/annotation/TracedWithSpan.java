/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import application.io.opentelemetry.extensions.auto.annotations.WithSpan;
import application.io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.TracingContextUtils;

public class TracedWithSpan {

  @WithSpan
  public String otel() {
    TracingContextUtils.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan("manualName")
  public String namedOtel() {
    TracingContextUtils.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan
  public String ignored() {
    TracingContextUtils.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan(kind = Kind.PRODUCER)
  public String oneOfAKind() {
    TracingContextUtils.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }
}
