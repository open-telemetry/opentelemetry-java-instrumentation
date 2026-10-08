/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.MINUTES;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pulsar.client.api.v5.Message;
import org.apache.pulsar.client.api.v5.MessageId;
import org.apache.pulsar.client.api.v5.Producer;
import org.apache.pulsar.client.api.v5.PulsarClient;
import org.apache.pulsar.client.api.v5.QueueConsumer;
import org.apache.pulsar.client.api.v5.config.BatchingPolicy;
import org.apache.pulsar.client.api.v5.schema.Schema;
import org.apache.pulsar.client.impl.v5.V5Interop;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PulsarClientV5Test extends AbstractPulsarClientTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sendAndReceive(boolean async) throws Exception {
    String topic = "topic://public/default/sendAndReceive-" + async;
    admin.scalableTopics().createScalableTopic(topic, 1);

    try (PulsarClient v5Client =
            PulsarClient.builder().serviceUrl("pulsar://" + brokerHost + ":" + brokerPort).build();
        QueueConsumer<String> queueConsumer =
            v5Client
                .newQueueConsumer(Schema.string())
                .topic(topic)
                .subscriptionName("test_sub")
                .subscribe();
        Producer<String> v5Producer =
            v5Client
                .newProducer(Schema.string())
                .topic(topic)
                .batchingPolicy(BatchingPolicy.ofDisabled())
                .create()) {
      MessageId messageId =
          async
              ? v5Producer.async().newMessage().value("test").send().get(1, MINUTES)
              : v5Producer.newMessage().value("test").send();
      Message<String> message =
          async
              ? queueConsumer.async().receive().get(1, MINUTES)
              : queueConsumer.receive(Duration.ofMinutes(1));
      assertThat(message.value()).isEqualTo("test");
      assertThat(message.id()).isEqualTo(messageId);
      queueConsumer.acknowledge(message.id());

      org.apache.pulsar.client.api.Message<String> segmentMessage =
          V5Interop.v4Message(message).orElseThrow();
      String segmentTopic = segmentMessage.getTopicName();
      String segmentMessageId = segmentMessage.getMessageId().toString();
      AtomicReference<SpanData> producerSpan = new AtomicReference<>();

      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(SpanKind.PRODUCER, SpanKind.CLIENT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("send " + segmentTopic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(segmentTopic, segmentMessageId, false)));
            producerSpan.set(trace.getSpan(0));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("receive " + segmentTopic)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasLinks(LinkData.create(asRemote(producerSpan.get().getSpanContext())))
                          .hasAttributesSatisfyingExactly(
                              receiveAttributes(segmentTopic, segmentMessageId, false))));
    }
  }
}
