/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Wrapper for KafkaTelemetry that can be injected into kafka configuration without breaking
 * serialization.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class KafkaTelemetrySupplier implements Supplier<KafkaTelemetry>, Serializable {
  private static final long serialVersionUID = 1L;
  private final transient KafkaTelemetry kafkaTelemetry;

  public KafkaTelemetrySupplier(KafkaTelemetry kafkaTelemetry) {
    Objects.requireNonNull(kafkaTelemetry);
    this.kafkaTelemetry = kafkaTelemetry;
  }

  @Override
  public KafkaTelemetry get() {
    return kafkaTelemetry;
  }

  private Object writeReplace() {
    // serialize this object to null
    return null;
  }
}
