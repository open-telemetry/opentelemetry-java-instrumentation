/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import application.io.opentelemetry.extensions.auto.annotations.WithSpan;
import application.io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;

public class TracedWithSpan {

  private static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto");

  @WithSpan
  public String otel() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan("manualName")
  public String namedOtel() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan
  public String ignored() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }

  @WithSpan(kind = Kind.PRODUCER)
  public String oneOfAKind() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Otel");
    return "hello!";
  }
}
