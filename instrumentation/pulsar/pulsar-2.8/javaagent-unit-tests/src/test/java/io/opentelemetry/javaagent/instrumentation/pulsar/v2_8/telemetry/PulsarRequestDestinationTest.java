/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;
import org.junit.jupiter.api.Test;

class PulsarRequestDestinationTest {

  private static final String TOPIC = "persistent://public/default/test";

  @Test
  void keepsNonPartitionedTopicName() {
    PulsarRequest request = request(message(TOPIC));

    assertThat(request.getDestination()).isEqualTo(TOPIC);
    assertThat(request.getDestinationPartitionId()).isNull();
  }

  @Test
  void separatesPartitionFromDestinationName() {
    String partitionTopic = TOPIC + "-partition-1";
    PulsarRequest request = request(message(partitionTopic));

    // the stable semantic conventions record the partition in messaging.destination.partition.id,
    // which is only unique within the destination name, so the destination name must not embed it
    assertThat(request.getDestination())
        .isEqualTo(emitStableMessagingSemconv() ? TOPIC : partitionTopic);
    assertThat(request.getDestinationPartitionId()).isEqualTo("1");
  }

  @Test
  void separatesPartitionFromBatchDestinationName() {
    String partitionTopic = TOPIC + "-partition-0";
    PulsarBatchRequest request = batchRequest(message(partitionTopic), message(partitionTopic));

    assertThat(request.getDestination())
        .isEqualTo(emitStableMessagingSemconv() ? TOPIC : partitionTopic);
    assertThat(request.getDestinationPartitionId()).isEqualTo("0");
  }

  @Test
  void hasNoPartitionWhenBatchSpansMultiplePartitions() {
    PulsarBatchRequest request =
        batchRequest(message(TOPIC + "-partition-0"), message(TOPIC + "-partition-1"));

    assertThat(request.getDestination()).isEqualTo(TOPIC);
    assertThat(request.getDestinationPartitionId()).isNull();
  }

  private static Message<?> message(String topic) {
    Message<?> message = mock(Message.class);
    when(message.getTopicName()).thenReturn(topic);
    return message;
  }

  private static PulsarRequest request(Message<?> message) {
    return PulsarRequest.create(message, null, "subscription");
  }

  private static PulsarBatchRequest batchRequest(Message<?>... messages) {
    List<Message<?>> messageList = asList(messages);
    @SuppressWarnings("unchecked")
    Messages<Object> batch = mock(Messages.class);
    when(batch.iterator()).thenAnswer(invocation -> messageList.iterator());
    @SuppressWarnings("unchecked")
    Consumer<Object> consumer = mock(Consumer.class);
    when(consumer.getSubscription()).thenReturn("subscription");
    return PulsarBatchRequest.create(batch, null, consumer);
  }
}
