/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.messaging;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinkExtractor;
import java.util.ArrayList;
import java.util.List;

/** A builder of {@link SpringIntegrationTracing}. */
public final class SpringIntegrationTracingBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-integration-core-4.1";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<MessageWithChannel, Void>> additionalAttributeExtractors =
      new ArrayList<>();

  SpringIntegrationTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public SpringIntegrationTracingBuilder addAttributesExtractor(
      AttributesExtractor<MessageWithChannel, Void> attributesExtractor) {
    additionalAttributeExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Returns a new {@link SpringIntegrationTracing} with the settings of this {@link
   * SpringIntegrationTracingBuilder}.
   */
  public SpringIntegrationTracing build() {
    MessageChannelSpanNameExtractor spanNameExtractor = new MessageChannelSpanNameExtractor();
    MessageSpanLinkExtractor spanLinkExtractor =
        new MessageSpanLinkExtractor(
            SpanLinkExtractor.fromUpstreamRequest(
                openTelemetry.getPropagators(), MessageHeadersGetter.INSTANCE));

    Instrumenter<MessageWithChannel, Void> instrumenter =
        Instrumenter.<MessageWithChannel, Void>newBuilder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addSpanLinkExtractor(spanLinkExtractor)
            .addAttributesExtractors(additionalAttributeExtractors)
            .newInstrumenter();
    return new SpringIntegrationTracing(openTelemetry.getPropagators(), instrumenter);
  }
}
