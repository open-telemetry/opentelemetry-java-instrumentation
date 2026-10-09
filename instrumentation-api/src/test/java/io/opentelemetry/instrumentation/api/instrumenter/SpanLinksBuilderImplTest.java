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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpanLinksBuilderImplTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void delegatesInvalidContexts(boolean hasTraceState) {
    SpanContext spanContext =
        SpanContext.create(
            TraceId.getInvalid(),
            SpanId.getInvalid(),
            TraceFlags.getDefault(),
            hasTraceState
                ? TraceState.builder().put("vendor", "value").build()
                : TraceState.getDefault());
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    SpanLinksBuilder links = new SpanLinksBuilderImpl(spanBuilder);
    Attributes attributes = Attributes.of(stringKey("messaging.message.id"), "message-1");

    assertThat(links.addLink(spanContext)).isSameAs(links);
    assertThat(links.addLink(spanContext, Attributes.empty())).isSameAs(links);
    assertThat(links.addLink(spanContext, attributes)).isSameAs(links);

    verify(spanBuilder).addLink(spanContext);
    verify(spanBuilder).addLink(spanContext, Attributes.empty());
    verify(spanBuilder).addLink(spanContext, attributes);
  }
}
