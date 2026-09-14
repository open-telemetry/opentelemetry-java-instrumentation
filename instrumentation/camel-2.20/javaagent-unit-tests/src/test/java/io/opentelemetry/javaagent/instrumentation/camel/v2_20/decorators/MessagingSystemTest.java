/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.javaagent.instrumentation.camel.v2_20.SpanDecorator;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingSystemTest {

  @ParameterizedTest
  @MethodSource("systemMappings")
  void systemMapping(String component, String expectedSystem) {
    SpanDecorator decorator = new DecoratorRegistry().forComponent(component);

    assertThat(decorator)
        .isInstanceOfSatisfying(
            MessagingSpanDecorator.class,
            messagingDecorator ->
                assertThat(messagingDecorator.getSystem()).isEqualTo(expectedSystem));
  }

  private static Stream<Arguments> systemMappings() {
    Stream.Builder<Arguments> mappings =
        Stream.<Arguments>builder()
            .add(argumentSet("legacy AMQP alias", "ampq", "amqp"))
            .add(argumentSet("CometD secure alias", "cometds", "cometd"))
            .add(argumentSet("Paho MQTT alias", "paho", "mqtt"))
            .add(argumentSet("simple JMS alias", "sjms", "jms"));
    if (emitStableMessagingSemconv()) {
      mappings.add(argumentSet("stable AMQP component", "amqp", "amqp"));
    }
    return mappings.build();
  }
}
