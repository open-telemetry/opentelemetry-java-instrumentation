/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SpanLinksBuilderImplTest {

  @ParameterizedTest
  @MethodSource("spanContexts")
  void delegatesLinkWithoutAttributes(SpanContext spanContext) {
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    SpanLinksBuilder spanLinks = new SpanLinksBuilderImpl(spanBuilder);

    assertThat(spanLinks.addLink(spanContext)).isSameAs(spanLinks);

    verify(spanBuilder).addLink(spanContext);
  }

  @ParameterizedTest
  @MethodSource("spanContexts")
  void delegatesLinkWithAttributes(SpanContext spanContext) {
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    SpanLinksBuilder spanLinks = new SpanLinksBuilderImpl(spanBuilder);
    Attributes attributes = Attributes.of(stringKey("messaging.message.id"), "message-1");

    assertThat(spanLinks.addLink(spanContext, attributes)).isSameAs(spanLinks);
    assertThat(spanLinks.addLink(spanContext, Attributes.empty())).isSameAs(spanLinks);

    verify(spanBuilder).addLink(spanContext, attributes);
    verify(spanBuilder).addLink(spanContext, Attributes.empty());
  }

  private static Stream<SpanContext> spanContexts() {
    return Stream.of(
        SpanContext.createFromRemoteParent(
            TraceId.fromLongs(0, 1),
            SpanId.fromLong(2),
            TraceFlags.getSampled(),
            TraceState.getDefault()),
        SpanContext.getInvalid(),
        SpanContext.create(
            TraceId.getInvalid(),
            SpanId.fromLong(2),
            TraceFlags.getDefault(),
            TraceState.getDefault()),
        SpanContext.create(
            TraceId.fromLongs(0, 1),
            SpanId.getInvalid(),
            TraceFlags.getDefault(),
            TraceState.getDefault()),
        SpanContext.create(
            TraceId.getInvalid(),
            SpanId.getInvalid(),
            TraceFlags.getDefault(),
            TraceState.builder().put("vendor", "value").build()),
        SpanContext.create(
            TraceId.getInvalid(),
            SpanId.getInvalid(),
            TraceFlags.getSampled(),
            TraceState.getDefault()));
  }
}
