/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyEnumeration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SjmsRegisteredListenerTest {

  private static final String CAMEL_INSTRUMENTATION_NAME = "io.opentelemetry.camel-2.20";
  private static final String JMS_INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";
  private static final boolean TEST_CAMEL_DISABLED = Boolean.getBoolean("testCamelDisabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final AtomicReference<MessageListener> registeredListener =
      new AtomicReference<>();
  private static final CountDownLatch received = new CountDownLatch(2);
  private static Message jmsMessage;

  @BeforeAll
  static void setUp() throws Exception {
    Queue destination = mock(Queue.class);
    when(destination.getQueueName()).thenReturn("reuseQueue");

    MessageConsumer messageConsumer = mock(MessageConsumer.class);
    doAnswer(
            invocation -> {
              registeredListener.set(invocation.getArgument(0));
              return null;
            })
        .when(messageConsumer)
        .setMessageListener(any());

    Session session = mock(Session.class);
    when(session.createQueue("reuseQueue")).thenReturn(destination);
    when(session.createConsumer(destination)).thenReturn(messageConsumer);

    Connection connection = mock(Connection.class);
    when(connection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(session);

    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    when(connectionFactory.createConnection()).thenReturn(connection);

    CamelContext camelContext = new DefaultCamelContext();
    SjmsComponent component = new SjmsComponent();
    component.setConnectionFactory(connectionFactory);
    camelContext.addComponent("sjms", component);
    camelContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("sjms:queue:reuseQueue").process(exchange -> received.countDown());
          }
        });
    camelContext.start();
    cleanup.deferAfterAll(camelContext::stop);

    jmsMessage = mock(Message.class);
    when(jmsMessage.getJMSDestination()).thenReturn(destination);
    when(jmsMessage.getJMSMessageID()).thenReturn("reused-message");
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
  }

  @Test
  void completesTwoCallbacksForReusedRawMessage() throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    MessageListener listener = registeredListener.get();
    assertThat(listener).isNotNull();

    listener.onMessage(jmsMessage);
    listener.onMessage(jmsMessage);

    assertThat(received.getCount()).isZero();
    String expectedInstrumentationName =
        TEST_CAMEL_DISABLED ? JMS_INSTRUMENTATION_NAME : CAMEL_INSTRUMENTATION_NAME;
    String unexpectedInstrumentationName =
        TEST_CAMEL_DISABLED ? CAMEL_INSTRUMENTATION_NAME : JMS_INSTRUMENTATION_NAME;
    assertProcessDuration(expectedInstrumentationName, 2);
    assertNoProcessDuration(unexpectedInstrumentationName);
  }

  private static void assertProcessDuration(String instrumentationName, long expectedCount) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "messaging.process.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .singleElement()
                        .satisfies(
                            point -> assertThat(point.getCount()).isEqualTo(expectedCount))));
  }

  private static void assertNoProcessDuration(String instrumentationName) {
    assertThat(testing.metrics())
        .noneMatch(
            metric ->
                instrumentationName.equals(metric.getInstrumentationScopeInfo().getName())
                    && metric.getName().equals("messaging.process.duration"));
  }
}
