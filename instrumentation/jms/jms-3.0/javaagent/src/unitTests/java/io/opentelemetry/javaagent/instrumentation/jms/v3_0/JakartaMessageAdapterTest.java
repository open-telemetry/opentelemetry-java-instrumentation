/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.jms.Message;
import org.junit.jupiter.api.Test;

class JakartaMessageAdapterTest {

  @Test
  void resetsConsumedMessagesWhenMessageIsReceivedAgain() {
    Message message = mock(Message.class);
    JakartaMessageAdapter adapter = JakartaMessageAdapter.create(message);

    assertThat(adapter.wereConsumedMessagesRecorded()).isFalse();
    adapter.prepareForReceive();
    adapter.markConsumedMessagesRecorded();
    assertThat(JakartaMessageAdapter.create(message).wereConsumedMessagesRecorded()).isTrue();

    assertThat(adapter.beginProcessing()).isTrue();
    adapter.endProcessing();
    assertThat(JakartaMessageAdapter.create(message).wereConsumedMessagesRecorded()).isTrue();

    adapter.prepareForReceive();
    assertThat(JakartaMessageAdapter.create(message).wereConsumedMessagesRecorded()).isFalse();
  }
}
