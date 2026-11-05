/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertCounter;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertHistogram;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoMetric;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.spring.jms.v2_0.AbstractJmsTest;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.CoreQueueConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;

class SpringJmsBaselineTest extends AbstractJmsTest {

  private static final String DESTINATION_NAME = "SpringListenerJms2";
  private static final String SUBSCRIPTION_NAME = "durable-subscription";
  private static final String JMS_INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";
  private static final String SPRING_JMS_INSTRUMENTATION_NAME = "io.opentelemetry.spring-jms-2.0";
  private static final String RECEIVE_TELEMETRY_PROPERTY =
      "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @TempDir private static File tempDir;

  @Test
  void receivesMessagesInSpringListener() throws Exception {
    HornetQServer server = createServer();
    cleanup.deferCleanup(server::stop);

    HornetQConnectionFactory connectionFactory = createConnectionFactory();
    cleanup.deferCleanup(connectionFactory::close);

    CountDownLatch consumerCreated = new CountDownLatch(1);
    CompletableFuture<SpanContext> listenerContext = new CompletableFuture<>();
    BaselineListenerContainer container = new BaselineListenerContainer(consumerCreated);
    container.setConnectionFactory(connectionFactory);
    container.setDestinationName(DESTINATION_NAME);
    container.setPubSubDomain(true);
    container.setSubscriptionDurable(true);
    container.setClientId("spring-jms-2-test");
    container.setDurableSubscriptionName(SUBSCRIPTION_NAME);
    @SuppressWarnings("rawtypes")
    SessionAwareMessageListener listener =
        (message, session) -> listenerContext.complete(Span.current().getSpanContext());
    container.setMessageListener(listener);
    cleanup.deferCleanup(container::destroy);
    container.afterPropertiesSet();
    container.start();
    assertThat(consumerCreated.await(10, SECONDS)).isTrue();

    JmsTemplate template = new JmsTemplate(connectionFactory);
    template.setPubSubDomain(true);
    testing.runWithSpan("parent", () -> template.convertAndSend(DESTINATION_NAME, "a message"));

    SpanContext processContext = listenerContext.get(10, SECONDS);
    assertThat(processContext.isValid()).isTrue();

    assertListenerSpans(processContext);
    assertMetrics();
  }

  private void assertListenerSpans(SpanContext processContext) {
    Consumer<TraceAssert> parentTraceAssertion =
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("send " + DESTINATION_NAME).hasParent(trace.getSpan(0)),
                span -> {
                  assertConsumerSpan(
                      span,
                      trace.getSpan(1),
                      trace.getSpan(1),
                      DESTINATION_NAME,
                      "process",
                      false,
                      null,
                      SUBSCRIPTION_NAME);
                  assertThat(processContext.getTraceId()).isEqualTo(trace.getSpan(2).getTraceId());
                  assertThat(processContext.getSpanId()).isEqualTo(trace.getSpan(2).getSpanId());
                });

    if (Boolean.getBoolean(RECEIVE_TELEMETRY_PROPERTY)) {
      AtomicReference<SpanData> producerSpan = new AtomicReference<>();
      testing.waitAndAssertSortedTraces(
          orderByRootSpanName("parent", "receive " + DESTINATION_NAME),
          trace -> {
            parentTraceAssertion.accept(trace);
            producerSpan.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      assertConsumerSpan(
                          span,
                          producerSpan.get(),
                          null,
                          DESTINATION_NAME,
                          "receive",
                          false,
                          null,
                          SUBSCRIPTION_NAME)));
    } else {
      testing.waitAndAssertTraces(parentTraceAssertion);
    }
  }

  private static void assertMetrics() {
    Attributes processAttributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "process")
            .put(stringKey("messaging.system.name"), "jms")
            .put(MESSAGING_DESTINATION_NAME, DESTINATION_NAME)
            .put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, SUBSCRIPTION_NAME)
            .build();
    assertHistogram(
        testing, SPRING_JMS_INSTRUMENTATION_NAME, "messaging.process.duration", processAttributes);

    Attributes receiveAttributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "receive")
            .put(stringKey("messaging.system.name"), "jms")
            .put(MESSAGING_DESTINATION_NAME, DESTINATION_NAME)
            .put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, SUBSCRIPTION_NAME)
            .build();
    if (Boolean.getBoolean(RECEIVE_TELEMETRY_PROPERTY)) {
      assertCounter(
          testing,
          JMS_INSTRUMENTATION_NAME,
          "messaging.client.consumed.messages",
          1,
          receiveAttributes);
      assertNoMetric(
          testing, SPRING_JMS_INSTRUMENTATION_NAME, "messaging.client.consumed.messages");
    } else {
      assertCounter(
          testing,
          SPRING_JMS_INSTRUMENTATION_NAME,
          "messaging.client.consumed.messages",
          1,
          processAttributes);
      assertNoMetric(testing, JMS_INSTRUMENTATION_NAME, "messaging.client.consumed.messages");
    }
  }

  private static HornetQServer createServer() throws Exception {
    Configuration config = new ConfigurationImpl();
    config.setBindingsDirectory(tempDir.getPath());
    config.setJournalDirectory(tempDir.getPath());
    config.setCreateBindingsDir(false);
    config.setCreateJournalDir(false);
    config.setSecurityEnabled(false);
    config.setPersistenceEnabled(false);
    config.setQueueConfigurations(
        singletonList(new CoreQueueConfiguration("someQueue", "someQueue", null, true)));
    config.setAcceptorConfigurations(
        singleton(new TransportConfiguration(InVMAcceptorFactory.class.getName())));

    HornetQServer server = HornetQServers.newHornetQServer(config);
    server.start();

    TransportConfiguration transportConfiguration =
        new TransportConfiguration(InVMConnectorFactory.class.getName());
    try (ServerLocator serverLocator =
        HornetQClient.createServerLocatorWithoutHA(transportConfiguration)) {
      try (ClientSessionFactory sessionFactory = serverLocator.createSessionFactory()) {
        try (ClientSession session = sessionFactory.createSession(false, false, false)) {
          String topic = "jms.topic." + DESTINATION_NAME;
          session.createQueue(topic, topic, true);
        }
      }
    }
    return server;
  }

  private static HornetQConnectionFactory createConnectionFactory() {
    return HornetQJMSClient.createConnectionFactoryWithoutHA(
        JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));
  }

  private static class BaselineListenerContainer extends DefaultMessageListenerContainer {
    private final CountDownLatch consumerCreated;

    private BaselineListenerContainer(CountDownLatch consumerCreated) {
      this.consumerCreated = consumerCreated;
    }

    @Override
    protected MessageConsumer createConsumer(Session session, Destination destination)
        throws JMSException {
      MessageConsumer consumer = super.createConsumer(session, destination);
      consumerCreated.countDown();
      return consumer;
    }
  }
}
