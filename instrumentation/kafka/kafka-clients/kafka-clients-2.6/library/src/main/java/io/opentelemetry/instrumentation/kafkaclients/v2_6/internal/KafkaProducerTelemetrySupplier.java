/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Wrapper for KafkaProducerTelemetry that can be injected into kafka configuration without breaking
 * serialization.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class KafkaProducerTelemetrySupplier
    implements Supplier<KafkaProducerTelemetry>, Serializable {

  private static final long serialVersionUID = 1L;

  private final KafkaProducerTelemetry producerTelemetry;

  public KafkaProducerTelemetrySupplier(KafkaProducerTelemetry producerTelemetry) {
    this.producerTelemetry = Objects.requireNonNull(producerTelemetry);
  }

  @Override
  public KafkaProducerTelemetry get() {
    return producerTelemetry;
  }

  private Object writeReplace() {
    // serialize this object to null
    return null;
  }
}
