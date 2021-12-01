/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;
import javax.annotation.Nullable;

class KafkaConsumerTimeExtractor implements TimeExtractor<ReceivedRecords, Void> {

  @Override
  public Instant extractStartTime(ReceivedRecords request) {
    return request.startTime();
  }

  @Override
  public Instant extractEndTime(
      ReceivedRecords request, @Nullable Void unused, @Nullable Throwable error) {
    return request.now();
  }
}
