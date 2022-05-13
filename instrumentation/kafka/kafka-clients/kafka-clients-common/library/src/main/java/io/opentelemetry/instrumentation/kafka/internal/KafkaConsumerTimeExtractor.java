/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;

class KafkaConsumerTimeExtractor implements TimeExtractor<ReceivedRecords, Void> {

  @Override
  public Instant extractStartTime(Context parentContext, ReceivedRecords request) {
    return request.startTime();
  }

  @Override
  public Instant extractEndTime(Context context, ReceivedRecords request) {
    return request.now();
  }
}
