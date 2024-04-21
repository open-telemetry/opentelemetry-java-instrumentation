/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;

public class SpringTemplateTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private HornetQServer server;
  private static final String messageText = "a message";
  private JmsTemplate template;
  private Session session;
  private Connection connection;

  @BeforeEach
  void setupSpec() throws Exception {
    File tempDir = Files.createTempDirectory("tmp").toFile();
    tempDir.deleteOnExit();

    Configuration config = new ConfigurationImpl();
    config.setBindingsDirectory(tempDir.getPath());
    config.setJournalDirectory(tempDir.getPath());
    config.setCreateBindingsDir(false);
    config.setCreateJournalDir(false);
    config.setSecurityEnabled(false);
    config.setPersistenceEnabled(false);
    List<CoreQueueConfiguration> list = new ArrayList<>();
    list.add(new CoreQueueConfiguration("someQueue", "someQueue", null, true));
    config.setQueueConfigurations(list);
    Set<TransportConfiguration> set = new java.util.HashSet<>();
    set.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
    config.setAcceptorConfigurations(set);

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

  @AfterEach
  void cleanupSpec() throws Exception {
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

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("SpringTemplateJms2 publish")
                      .hasKind(PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                          equalTo(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                              "SpringTemplateJms2"),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                              val -> val.isInstanceOf(String.class))));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  try {
                    span.hasName("SpringTemplateJms2 receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "SpringTemplateJms2"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                receivedMessage.getJMSMessageID()));
                  } catch (JMSException e) {
                    throw new RuntimeException(e);
                  }
                }));
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
              span ->
                  span.hasName("SpringTemplateJms2 publish")
                      .hasKind(PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                          equalTo(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                              "SpringTemplateJms2"),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                              val -> val.isInstanceOf(String.class))));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SpringTemplateJms2 receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "SpringTemplateJms2"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID, msgId.get()))),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("(temporary) publish")
                      .hasKind(PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                          equalTo(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                              "(temporary)"),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                          equalTo(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY, true),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                              val -> val.isInstanceOf(String.class))));
          tmpProducerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  try {
                    span.hasName("(temporary) receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(tmpProducerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "(temporary)"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                receivedMessage.getJMSMessageID()),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY,
                                true));
                  } catch (JMSException e) {
                    throw new RuntimeException(e);
                  }
                }));
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

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("SpringTemplateJms2 publish")
                      .hasKind(PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                          equalTo(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                              "SpringTemplateJms2"),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                              val -> val.isInstanceOf(String.class)),
                          equalTo(
                              stringArrayKey("messaging.header.test_message_header"),
                              Collections.singletonList("test")),
                          equalTo(
                              stringArrayKey("messaging.header.test_message_int_header"),
                              Collections.singletonList("1234"))));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  try {
                    span.hasName("SpringTemplateJms2 receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "SpringTemplateJms2"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                receivedMessage.getJMSMessageID()),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_header"),
                                Collections.singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_int_header"),
                                Collections.singletonList("1234")));
                  } catch (JMSException e) {
                    throw new RuntimeException(e);
                  }
                }));
  }
}
