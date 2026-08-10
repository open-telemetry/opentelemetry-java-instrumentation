/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaCommitRequest {

  @Nullable private final Map<?, ?> offsets;

  public static KafkaCommitRequest create(@Nullable Object argument) {
    return new KafkaCommitRequest(argument instanceof Map ? (Map<?, ?>) argument : null);
  }

  private KafkaCommitRequest(@Nullable Map<?, ?> offsets) {
    this.offsets = offsets;
  }

  @Nullable
  Map<?, ?> getOffsets() {
    return offsets;
  }
}
