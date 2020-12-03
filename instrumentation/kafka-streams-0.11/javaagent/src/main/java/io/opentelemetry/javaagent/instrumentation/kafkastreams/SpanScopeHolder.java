/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;

public class SpanScopeHolder {
  public static final ThreadLocal<SpanScopeHolder> HOLDER = new ThreadLocal<>();

  private SpanWithScope spanWithScope;

  public SpanWithScope getSpanWithScope() {
    return spanWithScope;
  }

  public void setSpanWithScope(SpanWithScope spanWithScope) {
    this.spanWithScope = spanWithScope;
  }
}
