/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;

/** A builder of {@link SpringKafkaTelemetry}. */
public final class SpringKafkaTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";

  private final OpenTelemetry openTelemetry;

  SpringKafkaTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Returns a new {@link SpringKafkaTelemetry} with the settings of this {@link
   * SpringKafkaTelemetryBuilder}.
   */
  public SpringKafkaTelemetry build() {
    KafkaInstrumenterFactory factory =
        new KafkaInstrumenterFactory(openTelemetry, INSTRUMENTATION_NAME)
            .setErrorCauseExtractor(SpringKafkaErrorCauseExtractor.INSTANCE);

    return new SpringKafkaTelemetry(
        factory.createConsumerProcessInstrumenter(), factory.createBatchProcessInstrumenter());
  }
}
