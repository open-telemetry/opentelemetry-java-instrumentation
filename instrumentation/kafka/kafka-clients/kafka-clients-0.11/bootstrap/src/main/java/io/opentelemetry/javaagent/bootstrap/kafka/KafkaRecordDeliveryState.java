/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

/** Tracks consumed-message accounting for one Kafka record delivery. */
public final class KafkaRecordDeliveryState {

  private boolean consumedMessagesRecorded;

  public void markConsumedMessagesRecorded() {
    consumedMessagesRecorded = true;
  }

  public boolean isConsumedMessagesRecorded() {
    return consumedMessagesRecorded;
  }
}
