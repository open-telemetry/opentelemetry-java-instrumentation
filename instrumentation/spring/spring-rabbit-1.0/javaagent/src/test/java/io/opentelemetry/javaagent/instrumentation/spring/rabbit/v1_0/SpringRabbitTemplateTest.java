/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class SpringRabbitTemplateTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void templateConsumerCanBeInstrumented() throws Exception {
    AMQConnection rabbitConnection = mock(AMQConnection.class);
    when(rabbitConnection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(rabbitConnection.getPort()).thenReturn(5672);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(executor::shutdownNow);
    StubChannel channel = new StubChannel(rabbitConnection);
    Connection connection = mock(Connection.class);
    when(connection.createChannel(anyBoolean())).thenReturn(channel);
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    when(connectionFactory.createConnection()).thenReturn(connection);
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setReceiveTimeout(10000);
    Future<?> delivery =
        executor.submit(
            () -> {
              channel
                  .registeredConsumer
                  .get()
                  .handleDelivery(
                      "template-consumer",
                      new Envelope(1, false, "", "template"),
                      new AMQP.BasicProperties.Builder().contentType("text/plain").build(),
                      "message".getBytes(UTF_8));
              return null;
            });

    Object message = template.receiveAndConvert("template");
    delivery.get();
    assertThat(message).isEqualTo("message");
    assertThat(channel.acknowledged).isTrue();
    assertThat(channel.cancelled).isTrue();
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("Channel.basicQos")),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("Channel.basicConsume")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv() ? "process template" : "template process")
                        .hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("Channel.basicCancel")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(emitStableMessagingSemconv() ? "ack" : "Channel.basicAck")));
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.rabbitmq-2.7",
          "messaging.client.consumed.messages",
          metrics ->
              metrics.satisfiesExactly(
                  metric ->
                      assertThat(metric)
                          .hasLongSumSatisfying(
                              sum -> sum.hasPointsSatisfying(point -> point.hasValue(1)))));
    }
  }

  private static class StubChannel extends ChannelN {
    private final CompletableFuture<Consumer> registeredConsumer = new CompletableFuture<>();
    private boolean acknowledged;
    private boolean cancelled;

    StubChannel(AMQConnection connection) {
      super(connection, 1, mock(ConsumerWorkService.class));
    }

    @Override
    public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException {}

    @Override
    public String basicConsume(
        String queue,
        boolean autoAck,
        String consumerTag,
        boolean noLocal,
        boolean exclusive,
        Map<String, Object> arguments,
        Consumer consumer)
        throws IOException {
      consumer.handleConsumeOk("template-consumer");
      registeredConsumer.complete(consumer);
      return "template-consumer";
    }

    @Override
    public void basicAck(long deliveryTag, boolean multiple) throws IOException {
      assertThat(deliveryTag).isEqualTo(1);
      assertThat(multiple).isFalse();
      acknowledged = true;
    }

    @Override
    public void basicCancel(String consumerTag) throws IOException {
      assertThat(consumerTag).isEqualTo("template-consumer");
      cancelled = true;
    }

    @Override
    public void close() throws IOException {}
  }
}
