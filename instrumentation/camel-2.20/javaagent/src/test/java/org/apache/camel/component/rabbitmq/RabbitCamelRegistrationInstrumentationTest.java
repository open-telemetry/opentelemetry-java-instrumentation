/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.camel.component.rabbitmq;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitCamelRegistrationInstrumentationTest {

  private static final boolean CAMEL_DISABLED = Boolean.getBoolean("testCamelDisabled");
  private static final boolean ADAPTER_DISABLED = Boolean.getBoolean("testAdapterDisabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void selectsOnlyCamelConsumerBeforeRegistration() throws Exception {
    Channel channel = mock(Channel.class);
    Connection connection = mock(Connection.class);
    when(connection.createChannel()).thenReturn(channel);
    when(connection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(channel.getConnection()).thenReturn(connection);
    RabbitMQEndpoint endpoint = mock(RabbitMQEndpoint.class);
    RabbitMQConsumer camelConsumer = mock(RabbitMQConsumer.class);
    when(camelConsumer.getConnection()).thenReturn(connection);
    when(camelConsumer.getEndpoint()).thenReturn(endpoint);
    RabbitConsumer consumer = new RabbitConsumer(camelConsumer);

    consumer.start();

    Class<?> virtualField =
        Class.forName("io.opentelemetry.javaagent.shaded.instrumentation.api.util.VirtualField");
    Object selection =
        virtualField
            .getMethod("find", Class.class, Class.class)
            .invoke(null, Consumer.class, Boolean.class);
    assertThat(virtualField.getMethod("get", Object.class).invoke(selection, consumer))
        .isEqualTo(
            (emitStableMessagingSemconv() && !CAMEL_DISABLED && !ADAPTER_DISABLED) ? true : null);
    assertThat(virtualField.getMethod("get", Object.class).invoke(selection, mock(Consumer.class)))
        .isNull();
  }
}
