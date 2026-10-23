/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.Test;

class RocketMqConsumerProcessAttributeGetterTest {

  private final RocketMqConsumerProcessAttributeGetter getter =
      new RocketMqConsumerProcessAttributeGetter();

  @Test
  void returnsFailureClassificationInsteadOfExceptionType() {
    assertThat(
            getter.getErrorType(
                mock(MessageView.class), ConsumeResult.FAILURE, new IllegalStateException()))
        .isEqualTo(ConsumeResult.FAILURE.name());
  }

  @Test
  void fallsBackToExceptionTypeWhenConsumeResultIsMissing() {
    assertThat(getter.getErrorType(mock(MessageView.class), null, new IllegalStateException()))
        .isEqualTo(IllegalStateException.class.getName());
  }
}
