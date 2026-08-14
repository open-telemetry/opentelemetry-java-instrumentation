/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.javaagent.instrumentation.camel.v2_20.SpanDecorator;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingPropagationTest {

  @ParameterizedTest
  @MethodSource("propagationSettings")
  void spanContextPropagation(String component, boolean expected) {
    SpanDecorator decorator = new DecoratorRegistry().forComponent(component);

    assertThat(decorator)
        .isInstanceOfSatisfying(
            MessagingSpanDecorator.class,
            messagingDecorator ->
                assertThat(messagingDecorator.isSpanContextPropagated()).isEqualTo(expected));
  }

  private static Stream<Arguments> propagationSettings() {
    return Stream.of(
        argumentSet("JMS propagates context", "jms", true),
        argumentSet("Camel MQTT cannot propagate context", "mqtt", false),
        argumentSet("Paho MQTT cannot propagate context", "paho", false));
  }
}
