/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import com.google.auto.value.AutoValue;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecords;

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

  public Instant endTime() {
    return timer().endTime();
  }
}
