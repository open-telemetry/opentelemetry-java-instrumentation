/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaHelper;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Wrapper for KafkaHelper that can be injected into kafka configuration without breaking
 * serialization.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class KafkaHelperSupplier implements Supplier<KafkaHelper>, Serializable {
  private static final long serialVersionUID = 1L;
  private final transient KafkaHelper kafkaHelper;

  public KafkaHelperSupplier(KafkaHelper kafkaHelper) {
    Objects.requireNonNull(kafkaHelper);
    this.kafkaHelper = kafkaHelper;
  }

  @Override
  public KafkaHelper get() {
    return kafkaHelper;
  }

  private Object writeReplace() {
    // serialize this object to null
    return null;
  }
}
