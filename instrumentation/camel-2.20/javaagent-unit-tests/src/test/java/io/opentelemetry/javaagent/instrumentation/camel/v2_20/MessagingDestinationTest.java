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
}
