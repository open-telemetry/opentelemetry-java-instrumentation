/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclientjava.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_DESTINATION;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_DESTINATION_KIND;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TYPE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingRocketmqMessageTypeValues.NORMAL;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractRocketMqClientTest {

  protected abstract InstrumentationExtension testing();

  // TODO(aaron-ai): replace it by the official image.
  private static final String IMAGE_NAME = "aaronai/rocketmq-proxy-it:v1.0.0";

  // We still need this container type to do fixed-port-mapping.
  @SuppressWarnings({"deprecation", "rawtypes", "resource"})
  @Test
  public void testSendMessage() throws ClientException {
    int proxyPort = PortUtils.findOpenPorts(4);
    int brokerPort = proxyPort + 1;
    int brokerHaPort = proxyPort + 2;
    int namesrvPort = proxyPort + 3;
    try (GenericContainer<?> container =
        new FixedHostPortGenericContainer(IMAGE_NAME)
            .withFixedExposedPort(proxyPort, proxyPort)
            .withEnv("rocketmq.broker.port", String.valueOf(brokerPort))
            .withEnv("rocketmq.proxy.port", String.valueOf(proxyPort))
            .withEnv("rocketmq.broker.ha.port", String.valueOf(brokerHaPort))
            .withEnv("rocketmq.namesrv.port", String.valueOf(namesrvPort))
            .withExposedPorts(proxyPort)) {
      // Start the container.
      container.start();
      String endpoints = "127.0.0.1:" + proxyPort;
      ClientConfiguration clientConfiguration =
          ClientConfiguration.newBuilder().setEndpoints(endpoints).build();
      // Inner topic of the container.
      String topic = "normal-topic-0";
      ClientServiceProvider provider = ClientServiceProvider.loadService();
      Producer producer =
          provider
              .newProducerBuilder()
              .setClientConfiguration(clientConfiguration)
              .setTopics(topic)
              .build();

      String tag = "tagA";
      String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
      byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
      Message message =
          provider
              .newMessageBuilder()
              .setTopic(topic)
              .setTag(tag)
              .setKeys(keys)
              .setBody(body)
              .build();

      SendReceipt sendReceipt = producer.send(message);
      testing()
          .waitAndAssertTraces(
              traceAssert ->
                  traceAssert.hasSpansSatisfyingExactly(
                      spanDataAssert ->
                          spanDataAssert
                              .hasName(topic + " send")
                              .hasStatus(StatusData.unset())
                              .hasAttributesSatisfyingExactly(
                                  equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                                  equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                                  equalTo(MESSAGING_ROCKETMQ_MESSAGE_TYPE, NORMAL),
                                  equalTo(MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, (long) body.length),
                                  equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                  equalTo(
                                      MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                                  equalTo(
                                      MESSAGING_DESTINATION_KIND,
                                      SemanticAttributes.MessagingDestinationKindValues.TOPIC),
                                  equalTo(MESSAGING_DESTINATION, topic))));
    }
  }
}
