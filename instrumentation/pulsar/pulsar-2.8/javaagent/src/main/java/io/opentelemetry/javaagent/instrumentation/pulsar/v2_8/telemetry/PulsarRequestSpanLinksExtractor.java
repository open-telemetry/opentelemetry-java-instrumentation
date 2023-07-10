/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;

final class PulsarRequestSpanLinksExtractor implements SpanLinksExtractor<PulsarRequest> {
  private final SpanLinksExtractor<PulsarRequest> singleRecordLinkExtractor;

  PulsarRequestSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, MessageTextMapGetter.INSTANCE);
  }

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, PulsarRequest req) {
    singleRecordLinkExtractor.extract(
        spanLinks, Context.root(), PulsarRequest.create(req.getMessage(), req.getUrlData()));
  }
}
