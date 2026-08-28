/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.msg.client.jakarta.jms.JmsReadablePropertyContext;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import com.ibm.msg.client.jakarta.wmq.common.CommonConstants;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Jakarta namespace twin of {@code IbmMqJmsTest} (javax, in the default {@code test} source set).
 * Runs in its own Gradle module ({@code instrumentation:ibmmq:ibmmq-jakarta:javaagent}) and its own
 * broker container -- see this module's {@code build.gradle.kts} for why the javax and jakarta MQ
 * clients must never share a test classpath (1487 identical fully qualified class names between the
 * two jars).
 */
class IbmMqJakartaJmsTest {

  private static final AttributeKey<String> QUEUE_MANAGER_ID =
      AttributeKey.stringKey("messaging.ibmmq.queue_manager.id");

  private static final String QMGR = "QM1";
  private static final String QUEUE = "DEV.QUEUE.1";
  private static final String MESSAGE_KEYED_QUEUE = "DEV.QUEUE.2";
  private static final String PASSWORD = "passw0rd";

  private static final boolean EXPERIMENTAL =
      Boolean.getBoolean("otel.instrumentation.ibmmq.experimental-span-attributes");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @SuppressWarnings("rawtypes")
  private static GenericContainer mq;

  private static MQConnectionFactory factory;
  private static String expectedQmid;

  @BeforeAll
  @SuppressWarnings("unchecked")
  static void startBroker() throws Exception {
    mq =
        new GenericContainer<>("icr.io/ibm-messaging/mq:9.4.3.0-r2")
            .withEnv("LICENSE", "accept")
            .withEnv("MQ_QMGR_NAME", QMGR)
            .withEnv("MQ_APP_PASSWORD", PASSWORD)
            .withExposedPorts(1414)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(5));
    mq.start();

    factory = new MQConnectionFactory();
    factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
    factory.setHostName(mq.getHost());
    factory.setPort(mq.getMappedPort(1414));
    factory.setChannel("DEV.APP.SVRCONN");
    factory.setQueueManager(QMGR);

    Exception last = null;
    for (int i = 0; i < 30; i++) {
      try (Connection connection = factory.createConnection("app", PASSWORD)) {
        expectedQmid =
            ((JmsReadablePropertyContext) connection)
                .getStringProperty(CommonConstants.WMQ_RESOLVED_QUEUE_MANAGER_ID)
                .trim();
        last = null;
        break;
      } catch (Exception e) {
        last = e;
        SECONDS.sleep(5);
      }
    }
    if (last != null) {
      throw last;
    }
    assertThat(expectedQmid).isNotEmpty();
  }

  @AfterAll
  static void stopBroker() {
    if (mq != null) {
      mq.stop();
    }
  }

  @Test
  void producerSpanCarriesQueueManagerId() throws Exception {
    try (Connection connection = factory.createConnection("app", PASSWORD)) {
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination destination = session.createQueue("queue:///" + QUEUE);
      MessageProducer producer = session.createProducer(destination);
      producer.send(session.createTextMessage("producer-qmid"));
    }

    SpanData span = awaitSpanOfKind(SpanKind.PRODUCER);
    assertQmid(span);
  }

  @Test
  void asyncListenerProcessSpanCarriesQueueManagerId() throws Exception {
    CountDownLatch delivered = new CountDownLatch(1);

    try (Connection connection = factory.createConnection("app", PASSWORD)) {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination destination = session.createQueue("queue:///" + QUEUE);

      MessageConsumer consumer = session.createConsumer(destination);
      // A named class, not a lambda: lambdas are generated at runtime and are not instrumentable.
      consumer.setMessageListener(new CountingListener(delivered));
      connection.start();

      try (Connection producerConnection = factory.createConnection("app", PASSWORD)) {
        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer =
            producerSession.createProducer(producerSession.createQueue("queue:///" + QUEUE));
        producer.send(producerSession.createTextMessage("listener-qmid"));
      }

      assertThat(delivered.await(60, SECONDS)).isTrue();
    }

    SpanData span = awaitSpanOfKind(SpanKind.CONSUMER);
    assertQmid(span);
  }

  @Test
  void messageKeyedListenerProcessSpanCarriesQueueManagerId() throws Exception {
    try (Connection producerConnection = factory.createConnection("app", PASSWORD)) {
      Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer =
          producerSession.createProducer(
              producerSession.createQueue("queue:///" + MESSAGE_KEYED_QUEUE));
      producer.send(producerSession.createTextMessage("message-keyed-qmid"));
    }

    try (Connection connection = factory.createConnection("app", PASSWORD)) {
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer =
          session.createConsumer(session.createQueue("queue:///" + MESSAGE_KEYED_QUEUE));

      // Mirrors Spring's default JmsListenerContainerFactory: poll with receive(), then invoke the
      // listener directly on the same call stack -- setMessageListener is never called.
      Message message = consumer.receive(30_000);
      assertThat(message).isNotNull();
      new CountingListener(new CountDownLatch(1)).onMessage(message);
    }

    SpanData span = awaitSpanForOperation("process");
    assertQmid(span);
  }

  private static void assertQmid(SpanData span) {
    if (EXPERIMENTAL) {
      assertThat(span.getAttributes().get(QUEUE_MANAGER_ID)).isEqualTo(expectedQmid);
    } else {
      assertThat(span.getAttributes().get(QUEUE_MANAGER_ID)).isNull();
    }
  }

  private static SpanData awaitSpanOfKind(SpanKind kind) {
    List<List<SpanData>> traces = testing.waitForTraces(1);
    Optional<SpanData> found =
        traces.stream().flatMap(List::stream).filter(s -> s.getKind() == kind).findFirst();
    if (found.isPresent()) {
      return found.get();
    }
    throw new AssertionError(
        "no span of kind "
            + kind
            + " among: "
            + traces.stream()
                .flatMap(List::stream)
                .map(
                    s ->
                        s.getKind()
                            + "/"
                            + s.getName()
                            + "/"
                            + s.getInstrumentationScopeInfo().getName())
                .collect(toList()));
  }

  private static SpanData awaitSpanForOperation(String operation) {
    List<SpanData> seen = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      List<List<SpanData>> traces = testing.waitForTraces(1);
      for (List<SpanData> trace : traces) {
        for (SpanData span : trace) {
          seen.add(span);
          if (asList(span.getName().split(" ")).contains(operation)) {
            return span;
          }
        }
      }
    }
    throw new AssertionError(
        "no span with operation token '"
            + operation
            + "' among: "
            + seen.stream().map(s -> s.getKind() + "/" + s.getName()).collect(toList()));
  }

  private static final class CountingListener implements MessageListener {
    private final CountDownLatch latch;

    CountingListener(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void onMessage(Message message) {
      latch.countDown();
    }
  }
}
