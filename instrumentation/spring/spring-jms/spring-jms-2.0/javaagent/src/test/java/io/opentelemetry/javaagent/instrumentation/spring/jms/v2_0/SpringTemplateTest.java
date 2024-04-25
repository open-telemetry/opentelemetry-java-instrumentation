/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.jms.v2_0.AbstractJmsTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;

class SpringTemplateTest extends AbstractJmsTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static HornetQServer server;
  private static final String messageText = "a message";
  private static JmsTemplate template;
  private static Session session;
  private static Connection connection;

  @BeforeAll
  static void setup() throws Exception {
    File tempDir = Files.createTempDirectory("tmp").toFile();
    tempDir.deleteOnExit();

    Configuration config = new ConfigurationImpl();
    config.setBindingsDirectory(tempDir.getPath());
    config.setJournalDirectory(tempDir.getPath());
    config.setCreateBindingsDir(false);
    config.setCreateJournalDir(false);
    config.setSecurityEnabled(false);
    config.setPersistenceEnabled(false);
    config.setQueueConfigurations(
        Collections.singletonList(
            new CoreQueueConfiguration("someQueue", "someQueue", null, true)));
    config.setAcceptorConfigurations(
        Collections.singleton(new TransportConfiguration(InVMAcceptorFactory.class.getName())));

    server = HornetQServers.newHornetQServer(config);
    server.start();

    ServerLocator serverLocator =
        HornetQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));
    ClientSessionFactory sf = serverLocator.createSessionFactory();
    ClientSession clientSession = sf.createSession(false, false, false);
    clientSession.createQueue("jms.queue.SpringTemplateJms2", "jms.queue.SpringTemplateJms2", true);
    clientSession.close();
    sf.close();
    serverLocator.close();

    HornetQConnectionFactory connectionFactory =
        HornetQJMSClient.createConnectionFactoryWithoutHA(
            JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));

    connection = connectionFactory.createConnection();
    connection.start();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    session.run();

    template = new JmsTemplate(connectionFactory);
    template.setReceiveTimeout(TimeUnit.SECONDS.toMillis(10));
  }

  @AfterAll
  static void cleanup() throws Exception {
    session.close();
    connection.close();
    server.stop();
  }

  @Test
  void sendingMessageToDestinationNameGeneratesSpans() throws JMSException {
    Queue queue = session.createQueue("SpringTemplateJms2");
    template.convertAndSend(queue, messageText);
    TextMessage receivedMessage = (TextMessage) template.receive(queue);

    assertThat(receivedMessage).isNotNull();
    assertThat(receivedMessage.getText()).isEqualTo(messageText);

    String receivedMsgId = receivedMessage.getJMSMessageID();
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertProducerSpan(span, "SpringTemplateJms2", false));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertConsumerSpan(
                        span,
                        producerSpan.get(),
                        null,
                        "SpringTemplateJms2",
                        "receive",
                        false,
                        receivedMsgId)));
  }

  @Test
  void sendAndReceiveMessageGeneratesSpans() throws JMSException {
    AtomicReference<String> msgId = new AtomicReference<>();
    Queue queue = session.createQueue("SpringTemplateJms2");
    Runnable msgSend =
        () -> {
          TextMessage msg = (TextMessage) template.receive(queue);
          assertThat(msg).isNotNull();
          try {
            assertThat(msg.getText()).isEqualTo(messageText);
            msgId.set(msg.getJMSMessageID());
            // There's a chance this might be reported last, messing up the assertion.
            template.send(
                msg.getJMSReplyTo(),
                (session) ->
                    Objects.requireNonNull(template.getMessageConverter())
                        .toMessage("responded!", session));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };
    Thread msgSendThread = new Thread(msgSend);
    msgSendThread.start();
    TextMessage receivedMessage =
        (TextMessage)
            template.sendAndReceive(
                queue,
                session ->
                    Objects.requireNonNull(template.getMessageConverter())
                        .toMessage(messageText, session));

    assertThat(receivedMessage).isNotNull();
    assertThat(receivedMessage.getText()).isEqualTo("responded!");

    String receivedMsgId = receivedMessage.getJMSMessageID();
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    AtomicReference<SpanData> tmpProducerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName(
            "SpringTemplateJms2 publish",
            "SpringTemplateJms2 receive",
            "(temporary) publish",
            "(temporary) receive"),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertProducerSpan(span, "SpringTemplateJms2", false));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertConsumerSpan(
                        span,
                        producerSpan.get(),
                        null,
                        "SpringTemplateJms2",
                        "receive",
                        false,
                        msgId.get())),
        trace -> {
          trace.hasSpansSatisfyingExactly(span -> assertProducerSpan(span, "(temporary)", false));
          tmpProducerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertConsumerSpan(
                        span,
                        tmpProducerSpan.get(),
                        null,
                        "(temporary)",
                        "receive",
                        false,
                        receivedMsgId)));
  }

  @Test
  void captureMessageHeaderAsSpanAttribute() throws JMSException {
    Queue queue = session.createQueue("SpringTemplateJms2");
    template.convertAndSend(
        queue,
        messageText,
        new MessagePostProcessor() {
          @Override
          public @NotNull Message postProcessMessage(@NotNull Message message) throws JMSException {
            message.setStringProperty("test_message_header", "test");
            message.setIntProperty("test_message_int_header", 1234);
            return message;
          }
        });
    TextMessage receivedMessage = (TextMessage) template.receive(queue);

    assertThat(receivedMessage).isNotNull();
    assertThat(receivedMessage.getText()).isEqualTo(messageText);

    String receivedMsgId = receivedMessage.getJMSMessageID();
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertProducerSpan(span, "SpringTemplateJms2", true));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertConsumerSpan(
                        span,
                        producerSpan.get(),
                        null,
                        "SpringTemplateJms2",
                        "receive",
                        true,
                        receivedMsgId)));
  }
}
