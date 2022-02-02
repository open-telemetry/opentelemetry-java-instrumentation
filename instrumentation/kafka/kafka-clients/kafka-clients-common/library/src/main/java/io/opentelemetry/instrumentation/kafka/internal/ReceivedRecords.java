/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import com.google.auto.value.AutoValue;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class ReceivedRecords {

  public static ReceivedRecords create(ConsumerRecords<?, ?> records, Timer timer) {
    return new AutoValue_ReceivedRecords(records, timer);
  }

  public abstract ConsumerRecords<?, ?> records();

  abstract Timer timer();

  public Instant startTime() {
    return timer().startTime();
  }

  public Instant now() {
    return timer().now();
  }
}
