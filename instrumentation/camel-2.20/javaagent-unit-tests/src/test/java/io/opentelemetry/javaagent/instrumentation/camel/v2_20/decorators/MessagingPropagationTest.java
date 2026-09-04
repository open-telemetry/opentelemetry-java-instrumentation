/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.instrumentation.camel.v2_20.SpanDecorator;
import java.util.stream.Stream;
import org.apache.camel.Endpoint;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingPropagationTest {

  @ParameterizedTest
  @MethodSource("propagationSettings")
  void spanContextPropagation(String component, String endpointUri, boolean expected) {
    SpanDecorator decorator = new DecoratorRegistry().forComponent(component);
    Endpoint endpoint = mock(Endpoint.class);
    when(endpoint.getEndpointUri()).thenReturn(endpointUri);

    assertThat(decorator)
        .isInstanceOfSatisfying(
            MessagingSpanDecorator.class,
            messagingDecorator ->
                assertThat(messagingDecorator.isSpanContextPropagated(endpoint))
                    .isEqualTo(expected));
  }

  private static Stream<Arguments> propagationSettings() {
    return Stream.of(
        argumentSet("JMS propagates context", "jms", "jms:queue", true),
        argumentSet("Camel MQTT cannot propagate context", "mqtt", "mqtt:topic", false),
        argumentSet("Paho MQTT cannot propagate context", "paho", "paho:topic", false),
        argumentSet("IronMQ drops headers by default", "ironmq", "ironmq:queue", false),
        argumentSet(
            "IronMQ propagates preserved headers",
            "ironmq",
            "ironmq:queue?preserveHeaders=true",
            true));
  }
}
