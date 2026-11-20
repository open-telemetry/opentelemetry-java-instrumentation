/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.AbstractSpringIntegration41Test.assertPropagation;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertNoMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertProcessMetrics;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetAddress;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.channel.DirectChannel;

class SpringIntegrationAmqp41Test {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldDeliverThroughAmqpAdapter(boolean withRabbitProcessing) throws Throwable {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    cleanup.deferCleanup(context);
    context.setId("application");
    context.register(AbstractSpringIntegration41Test.Channels.class);
    context.refresh();
    DirectChannel output = context.getBean("directChannel", DirectChannel.class);
    CapturingMessageHandler captured = new CapturingMessageHandler();
    output.subscribe(captured);

    TestContainer container = new TestContainer();
    cleanup.deferCleanup(container::destroy);
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(container);
    cleanup.deferCleanup(adapter::stop);
    adapter.setBeanFactory(context.getBeanFactory());
    adapter.setOutputChannel(output);
    adapter.afterPropertiesSet();
    adapter.start();

    MessageProperties properties = new MessageProperties();
    properties.setReceivedRoutingKey("input");
    properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
    Message message = new Message("test".getBytes(UTF_8), properties);

    Context before = Context.current();
    if (withRabbitProcessing) {
      container.deliver(message);
    } else {
      ((ChannelAwareMessageListener) container.getMessageListener())
          .onMessage(message, newChannel());
    }
    assertThat(Context.current()).isSameAs(before);
    assertThat(captured.join().getPayload()).isEqualTo("test");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName(
                          withRabbitProcessing
                              ? "process input"
                              : "process application.directChannel")
                      .hasKind(SpanKind.CONSUMER)
                      .hasNoParent();
                  assertPropagation(captured.join(), trace.getSpan(0));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));
    if (withRabbitProcessing) {
      assertNoMetrics(testing);
    } else {
      assertProcessMetrics(testing, "application.directChannel", false);
    }
  }

  private static Channel newChannel() {
    Connection connection = mock(Connection.class);
    when(connection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(connection.getPort()).thenReturn(5672);
    Channel channel = mock(Channel.class);
    when(channel.getConnection()).thenReturn(connection);
    return channel;
  }

  private static final class TestContainer extends AbstractMessageListenerContainer {
    TestContainer() {
      setConnectionFactory(mock(ConnectionFactory.class));
    }

    @Override
    protected void doInitialize() {}

    @Override
    protected void doShutdown() {}

    void deliver(Message message) throws Throwable {
      executeListener(newChannel(), message);
    }
  }
}
