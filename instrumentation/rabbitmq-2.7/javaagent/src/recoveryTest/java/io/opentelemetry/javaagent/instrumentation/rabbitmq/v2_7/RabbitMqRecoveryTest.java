/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoverableConnection;
import com.rabbitmq.client.RecoveryListener;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitMqRecoveryTest extends AbstractRabbitMqTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  // Recovery replaces the inner AMQConnection, so its vhost must be captured again on reconnect.
  @Test
  void testVhostSurvivesAutomaticRecovery() throws Exception {
    ConnectionFactory recoveringFactory = new ConnectionFactory();
    recoveringFactory.setHost(rabbitMqHost);
    recoveringFactory.setPort(rabbitMqPort);
    recoveringFactory.setVirtualHost("otel-test");
    recoveringFactory.setAutomaticRecoveryEnabled(true);
    recoveringFactory.setNetworkRecoveryInterval(200);

    Connection recoveringConnection = recoveringFactory.newConnection();
    cleanup.deferCleanup(recoveringConnection);
    Channel recoveringChannel = recoveringConnection.createChannel();

    CountDownLatch recovered = new CountDownLatch(1);
    ((RecoverableConnection) recoveringConnection)
        .addRecoveryListener(
            new RecoveryListener() {
              @Override
              public void handleRecovery(Recoverable recoverable) {
                recovered.countDown();
              }

              @Override
              public void handleRecoveryStarted(Recoverable recoverable) {}
            });

    rabbitMqContainer.execInContainer(
        "rabbitmqctl", "close_all_connections", "-p", "otel-test", "otel recovery test");
    assertThat(recovered.await(10, SECONDS)).isTrue();

    testing.clearData();
    recoveringChannel.queueDeclare();
    String recoveredPeerAddress = recoveringChannel.getConnection().getAddress().getHostAddress();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttributesSatisfyingExactly(
                        equalTo(stringKey("messaging.system.name"), "rabbitmq"),
                        satisfies(
                            NETWORK_PEER_ADDRESS, val -> val.isIn(recoveredPeerAddress, null)),
                        satisfies(NETWORK_TYPE, val -> val.isIn("ipv4", "ipv6", null)),
                        satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()),
                        equalTo(stringKey("rabbitmq.command"), experimental("queue.declare")),
                        equalTo(
                            stringKey("rabbitmq.vhost.name"), experimental("otel-test")),
                        equalTo(
                            stringKey("rabbitmq.cluster.name"),
                            experimental("otel-test-cluster")))));
  }
}
