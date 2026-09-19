/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaRecordDeliveryState;
import org.junit.jupiter.api.Test;

class KafkaRecordDeliveryStateTest {

  @Test
  void recordsConsumedMessageAccounting() {
    KafkaRecordDeliveryState state = new KafkaRecordDeliveryState();

    assertThat(state.isConsumedMessagesRecorded()).isFalse();

    state.markConsumedMessagesRecorded();

    assertThat(state.isConsumedMessagesRecorded()).isTrue();
  }
}
