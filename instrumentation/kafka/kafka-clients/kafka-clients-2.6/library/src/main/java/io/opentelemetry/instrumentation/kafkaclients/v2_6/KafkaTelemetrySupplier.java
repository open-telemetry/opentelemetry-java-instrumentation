/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

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
final class KafkaTelemetrySupplier implements Supplier<Object>, Serializable {
  private static final long serialVersionUID = 1L;
  private final transient Object kafkaTelemetry;

  KafkaTelemetrySupplier(Object kafkaTelemetry) {
    Objects.requireNonNull(kafkaTelemetry);
    this.kafkaTelemetry = kafkaTelemetry;
  }

  @Override
  public Object get() {
    return kafkaTelemetry;
  }

  private Object writeReplace() {
    // serialize this object to null
    return null;
  }
}
