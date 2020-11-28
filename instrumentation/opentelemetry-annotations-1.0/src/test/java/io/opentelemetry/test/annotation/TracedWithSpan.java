/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.extension.annotations.WithSpan;

public class TracedWithSpan {

  @WithSpan
  public String otel() {
    return "hello!";
  }

  @WithSpan("manualName")
  public String namedOtel() {
    return "hello!";
  }

  @WithSpan
  public String ignored() {
    return "hello!";
  }

  @WithSpan(kind = Kind.PRODUCER)
  public String oneOfAKind() {
    return "hello!";
  }
}
