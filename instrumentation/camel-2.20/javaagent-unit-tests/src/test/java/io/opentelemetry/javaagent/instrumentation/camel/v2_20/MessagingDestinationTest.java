/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators.DecoratorRegistry;
import java.util.stream.Stream;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingDestinationTest {

  @ParameterizedTest
  @MethodSource("destinations")
  void stableDestination(String component, String endpointUri, String expectedDestination) {
    Endpoint endpoint = mock(Endpoint.class);
    when(endpoint.getEndpointUri()).thenReturn(endpointUri);

    CamelRequest request =
        CamelRequest.create(
            new DecoratorRegistry().forComponent(component),
            mock(Exchange.class),
            endpoint,
            CamelDirection.OUTBOUND,
            SpanKind.PRODUCER);

    assertThat(request.getMessagingDestination())
        .isEqualTo(emitStableMessagingSemconv() ? expectedDestination : null);
  }

  private static Stream<Arguments> destinations() {
    return Stream.of(
        argumentSet("JMS queue", "jms", "jms:queue:myQueue", "myQueue"),
        argumentSet("JMS topic", "jms", "jms:topic:myTopic", "myTopic"),
        argumentSet("AMQP queue", "amqp", "amqp:queue:myQueue", "myQueue"),
        argumentSet("AMQP topic", "amqp", "amqp:topic:myTopic", "myTopic"),
        argumentSet("legacy AMQP alias queue", "ampq", "ampq:queue:myQueue", "myQueue"));
  }

  @ParameterizedTest
  @MethodSource("rabbitMqDestinations")
  void stableRabbitMqDestination(
      CamelDirection camelDirection,
      String endpointUri,
      String headerExchange,
      String headerRoutingKey,
      String expectedDestination) {
    Endpoint endpoint = mock(Endpoint.class);
    when(endpoint.getEndpointUri()).thenReturn(endpointUri);
    Exchange exchange = mock(Exchange.class);
    Message message = mock(Message.class);
    when(exchange.getIn()).thenReturn(message);
    when(message.getHeader("rabbitmq.EXCHANGE_NAME", String.class)).thenReturn(headerExchange);
    when(message.getHeader("rabbitmq.ROUTING_KEY", String.class)).thenReturn(headerRoutingKey);

    CamelRequest request =
        CamelRequest.create(
            new DecoratorRegistry().forComponent("rabbitmq"),
            exchange,
            endpoint,
            camelDirection,
            camelDirection == CamelDirection.OUTBOUND ? SpanKind.PRODUCER : SpanKind.CONSUMER);

    assertThat(request.getMessagingDestination())
        .isEqualTo(emitStableMessagingSemconv() ? expectedDestination : null);
  }

  private static Stream<Arguments> rabbitMqDestinations() {
    return Stream.of(
        argumentSet(
            "outbound queue is ignored",
            CamelDirection.OUTBOUND,
            "rabbitmq:localhost:5672/orders?routingKey=created&queue=workers",
            null,
            null,
            "orders:created"),
        argumentSet(
            "outbound headers override endpoint",
            CamelDirection.OUTBOUND,
            "rabbitmq:localhost:5672/orders?routingKey=created",
            "priority-orders",
            "priority-created",
            "priority-orders:priority-created"),
        argumentSet(
            "outbound bridge endpoint ignores headers",
            CamelDirection.OUTBOUND,
            "rabbitmq:localhost:5672/orders?routingKey=created&bridgeEndpoint=true",
            "priority-orders",
            "priority-created",
            "orders:created"),
        argumentSet(
            "inbound queue is included",
            CamelDirection.INBOUND,
            "rabbitmq:localhost:5672/orders?routingKey=created&queue=workers",
            "orders",
            "created",
            "orders:created:workers"),
        argumentSet(
            "inbound queue matching routing key is not duplicated",
            CamelDirection.INBOUND,
            "rabbitmq:localhost:5672/orders?routingKey=workers&queue=workers",
            "orders",
            "workers",
            "orders:workers"),
        argumentSet(
            "inbound bridge endpoint does not ignore headers",
            CamelDirection.INBOUND,
            "rabbitmq:localhost:5672/orders?routingKey=created&queue=workers&bridgeEndpoint=true",
            "priority-orders",
            "priority-created",
            "priority-orders:priority-created:workers"),
        argumentSet(
            "empty inbound destination",
            CamelDirection.INBOUND,
            "rabbitmq:localhost:5672/",
            null,
            null,
            null));
  }
}
