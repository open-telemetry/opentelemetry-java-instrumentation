/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
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
    Instrumenter<MessageWithChannel, Void> instrumenter =
        Instrumenter.<MessageWithChannel, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, new MessageChannelSpanNameExtractor())
            .addAttributesExtractors(additionalAttributeExtractors)
            .addAttributesExtractor(new SpringMessagingAttributesExtractor())
            .newConsumerInstrumenter(MessageHeadersGetter.INSTANCE);
    return new SpringIntegrationTracing(openTelemetry.getPropagators(), instrumenter);
  }
}
