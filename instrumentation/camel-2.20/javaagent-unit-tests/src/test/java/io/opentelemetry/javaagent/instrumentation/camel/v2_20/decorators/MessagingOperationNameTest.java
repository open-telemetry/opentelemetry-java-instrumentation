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

class MessagingOperationNameTest {

  @ParameterizedTest
  @MethodSource("operationNames")
  void operationName(String component, String expectedOperationName) {
    SpanDecorator decorator = new DecoratorRegistry().forComponent(component);

    assertThat(decorator)
        .isInstanceOfSatisfying(
            MessagingSpanDecorator.class,
            messagingDecorator ->
                assertThat(messagingDecorator.getSendOperationName())
                    .isEqualTo(expectedOperationName));
  }

  private static Stream<Arguments> operationNames() {
    return Stream.of(
        argumentSet("default send operation", "jms", "send"),
        argumentSet("RabbitMQ publish operation", "rabbitmq", "publish"));
  }
}
