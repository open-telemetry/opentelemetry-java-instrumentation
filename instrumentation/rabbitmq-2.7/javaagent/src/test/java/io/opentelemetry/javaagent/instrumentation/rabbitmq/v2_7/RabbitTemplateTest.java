/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitMqMetricsAssertions.assertReceiveMetrics;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitTemplateTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void receiveWithoutSpringInstrumentation() {
    AMQConnection rabbitConnection = mock(AMQConnection.class);
    when(rabbitConnection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(rabbitConnection.getPort()).thenReturn(5672);
    StubChannel channel = new StubChannel(rabbitConnection);
    Connection connection = mock(Connection.class);
    when(connection.createChannel(anyBoolean())).thenReturn(channel);
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    when(connectionFactory.createConnection()).thenReturn(connection);
    RabbitTemplate template = new RabbitTemplate(connectionFactory);

    assertThat(template.receiveAndConvert("template")).isEqualTo("message");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv() ? "receive template" : "template receive")
                        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                        .hasNoParent()));
    assertReceiveMetrics(testing, "template", null, 1);
  }

  private static class StubChannel extends ChannelN {
    StubChannel(AMQConnection connection) {
      super(connection, 1, mock(ConsumerWorkService.class));
    }

    @Override
    public GetResponse basicGet(String queue, boolean autoAck) throws IOException {
      assertThat(queue).isEqualTo("template");
      assertThat(autoAck).isTrue();
      return new GetResponse(
          new Envelope(1, false, "", queue),
          new AMQP.BasicProperties.Builder().contentType("text/plain").build(),
          "message".getBytes(UTF_8),
          0);
    }

    @Override
    public void close() throws IOException {}
  }
}
