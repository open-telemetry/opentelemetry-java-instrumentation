/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that publish/consume spans have at least one of the new network semantic-convention
 * keys: - network.peer.address / network.peer.port - OR server.address / server.port
 *
 * <p>Test-only; no production code changed.
 */
public class RabbitMqNewNetAttributesTest {

  @RegisterExtension
  static final InstrumentationExtension testing = InstrumentationExtension.create();

  static final DockerImageName IMAGE = DockerImageName.parse("rabbitmq:3.13-alpine");

  @Test
  void publish_and_consume_contains_new_network_attributes() throws Exception {
    try (RabbitMQContainer rabbit = new RabbitMQContainer(IMAGE)) {
      rabbit.start();

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost(rabbit.getHost());
      factory.setPort(rabbit.getAmqpPort());

      try (Connection connection = factory.newConnection();
          Channel channel = connection.createChannel()) {

        String queue = "q-" + UUID.randomUUID();
        channel.queueDeclare(queue, false, false, true, null);

        // produce + consume
        channel.basicPublish("", queue, null, "hello".getBytes(StandardCharsets.UTF_8));
        GetResponse res = null;
        for (int i = 0; i < 30 && res == null; i++) {
          res = channel.basicGet(queue, true);
          Thread.sleep(100);
        }

        // Wait for spans, then assert at least one has new network attributes
        testing.waitAndAssertTraces(
            traces -> traces.anySatisfy(trace -> trace.spans().size() >= 1));

        List<io.opentelemetry.sdk.trace.data.SpanData> spans = testing.getExportedSpans();

        AttributeKey<String> NET_PEER_ADDR = AttributeKey.stringKey("network.peer.address");
        AttributeKey<Long> NET_PEER_PORT = AttributeKey.longKey("network.peer.port");
        AttributeKey<String> SERVER_ADDR = AttributeKey.stringKey("server.address");
        AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");

        boolean foundNewNetAttr =
            spans.stream()
                .anyMatch(
                    span -> {
                      var attrs = span.getAttributes();
                      boolean hasPeer =
                          attrs.get(NET_PEER_ADDR) != null || attrs.get(NET_PEER_PORT) != null;
                      boolean hasServer =
                          attrs.get(SERVER_ADDR) != null || attrs.get(SERVER_PORT) != null;
                      return hasPeer || hasServer;
                    });

        assertThat(foundNewNetAttr)
            .as("at least one span should carry new network attributes")
            .isTrue();
      }
    }
  }
}
