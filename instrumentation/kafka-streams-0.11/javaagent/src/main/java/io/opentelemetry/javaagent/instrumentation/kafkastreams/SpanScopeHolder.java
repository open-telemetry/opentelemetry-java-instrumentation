/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

public class SpanScopeHolder {
  public static final ThreadLocal<SpanScopeHolder> HOLDER = new ThreadLocal<>();

  private Span span;
  private Scope scope;

  public void closeScope() {
    scope.close();
  }

  public Span getSpan() {
    return span;
  }

  public void set(Span span, Scope scope) {
    this.span = span;
    this.scope = scope;
  }
}
