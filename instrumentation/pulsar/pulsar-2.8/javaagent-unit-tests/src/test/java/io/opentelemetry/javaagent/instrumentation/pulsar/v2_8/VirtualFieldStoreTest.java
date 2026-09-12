/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.pulsar.client.api.Message;
import org.junit.jupiter.api.Test;

class VirtualFieldStoreTest {

  @Test
  void tracksConsumedMessagesClaim() {
    Message<?> message = mock(Message.class);

    assertThat(VirtualFieldStore.wereConsumedMessagesRecorded(message)).isFalse();

    VirtualFieldStore.markConsumedMessagesRecorded(message);
    assertThat(VirtualFieldStore.wereConsumedMessagesRecorded(message)).isTrue();

    VirtualFieldStore.clear(message);
    assertThat(VirtualFieldStore.wereConsumedMessagesRecorded(message)).isFalse();
  }
}
