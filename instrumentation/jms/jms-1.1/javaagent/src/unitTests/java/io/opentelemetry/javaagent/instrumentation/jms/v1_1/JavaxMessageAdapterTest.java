/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.jms.Message;
import org.junit.jupiter.api.Test;

class JavaxMessageAdapterTest {

  @Test
  void resetsConsumedMessagesWhenMessageIsReceivedAgain() {
    Message message = mock(Message.class);
    JavaxMessageAdapter adapter = JavaxMessageAdapter.create(message);

    assertThat(adapter.wereConsumedMessagesRecorded()).isFalse();
    adapter.prepareForReceive();
    adapter.markConsumedMessagesRecorded();
    assertThat(JavaxMessageAdapter.create(message).wereConsumedMessagesRecorded()).isTrue();

    assertThat(adapter.beginProcessing()).isTrue();
    adapter.endProcessing();
    assertThat(JavaxMessageAdapter.create(message).wereConsumedMessagesRecorded()).isTrue();

    adapter.prepareForReceive();
    assertThat(JavaxMessageAdapter.create(message).wereConsumedMessagesRecorded()).isFalse();
  }
}
