/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;

final class SpanLinksBuilderImpl implements SpanLinksBuilder {
  private final SpanBuilder spanBuilder;

  SpanLinksBuilderImpl(SpanBuilder spanBuilder) {
    this.spanBuilder = spanBuilder;
  }

  @Override
  public SpanLinksBuilder addLink(SpanContext spanContext) {
    spanBuilder.addLink(spanContext);
    return this;
  }

  @Override
  public SpanLinksBuilder addLink(SpanContext spanContext, Attributes attributes) {
    spanBuilder.addLink(spanContext, attributes);
    return this;
  }
}
