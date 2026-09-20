/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v2_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Exercises the JMS 2.0 simplified API ({@code JMSContext} / {@code JMSProducer} / {@code
 * JMSConsumer}) against an in-VM HornetQ broker.
 *
 * <p>The point of these tests is the span *count*. HornetQ implements the simplified API on top of
 * the classic one, so without a shared call depth between the two instrumentations every send and
 * receive would produce two spans.
 */
class JmsSimplifiedApiTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static HornetQConnectionFactory connectionFactory;
  private static Connection connection;
  private static Session session;

  @BeforeAll
  static void setUp() throws Exception {
    File tempDir = Files.createTempDirectory("jms2SimplifiedTempDir").toFile();
    tempDir.deleteOnExit();

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
        new HashSet<>(
            singletonList(new TransportConfiguration(InVMAcceptorFactory.class.getName()))));

    HornetQServer server = HornetQServers.newHornetQServer(config);
    server.start();
    cleanup.deferAfterAll(server::stop);

    ServerLocator serverLocator =
        HornetQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));
    ClientSessionFactory sf = serverLocator.createSessionFactory();
    ClientSession clientSession = sf.createSession(false, false, false);
    clientSession.createQueue("jms.queue.someQueue", "jms.queue.someQueue", true);
    clientSession.createQueue("jms.topic.someTopic", "jms.topic.someTopic", true);
    clientSession.close();
    sf.close();
    serverLocator.close();

    connectionFactory =
        HornetQJMSClient.createConnectionFactoryWithoutHA(
            JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));
    connection = connectionFactory.createConnection();
    connection.setClientID("jms-2-simplified-test");
    connection.start();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    session.run();
    cleanup.deferAfterAll(connectionFactory::close);
    cleanup.deferAfterAll(connection);
    cleanup.deferAfterAll(session);
  }

  @Test
  void producerSendAndConsumerReceiveEachEmitExactlyOneSpan() throws JMSException {
    Destination destination = session.createQueue("someQueue");
    TextMessage sentMessage = session.createTextMessage("hello there");

    JMSContext context = connectionFactory.createContext();
    cleanup.deferCleanup(context);
    JMSConsumer consumer = context.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    testing.runWithSpan(
        "producer parent", () -> context.createProducer().send(destination, sentMessage));

    Message received = testing.runWithSpan("consumer parent", () -> consumer.receive(10_000));
    assertThat(received).isNotNull();
    assertThat(((TextMessage) received).getText()).isEqualTo("hello there");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv() ? "send someQueue" : "someQueue publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("consumer parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "receive someQueue"
                                : "someQueue receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldNotEmitTelemetryOnEmptyReceive() throws JMSException {
    Destination destination = session.createTopic("someTopic");

    JMSContext context = connectionFactory.createContext();
    cleanup.deferCleanup(context);
    JMSConsumer consumer = context.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    assertThat(consumer.receive(100)).isNull();
    assertThat(consumer.receiveNoWait()).isNull();

    testing.waitForTraces(0);
  }
}
