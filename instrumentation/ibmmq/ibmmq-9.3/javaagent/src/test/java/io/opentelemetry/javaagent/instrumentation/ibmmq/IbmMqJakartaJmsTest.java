/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Jakarta namespace twin of {@code IbmMqJmsTest} (javax, in the default {@code test} source set).
 * Runs in its own Gradle module ({@code instrumentation:ibmmq:ibmmq-9.3:javaagent}) and its own
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

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
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
    assertMessagingSystem(span);
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
    assertMessagingSystem(span);
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
    assertMessagingSystem(span);
  }

  @Test
  void listenerAssociationRetriesAfterUnavailableFirstRead() {
    // A unit-level check (no broker span involved): proves that a listener whose first
    // stamp()-time readQmid() attempt comes up empty is not permanently locked out -- a later
    // delivery must retry the read and can still succeed. Do not collapse this back to a single
    // stamp() call asserting reads() == 1: that only proves a read happens once, never that a
    // failed read is retried on the NEXT delivery instead of caching the failure.
    FlakyPropertyContext consumer = new FlakyPropertyContext(expectedQmid);
    CountingListener listener = new CountingListener(new CountDownLatch(1));

    // associate() only stores the (weak) consumer reference; it must never itself read the QMID.
    IbmMqJakartaJmsListenerQmid.associate(consumer, listener);
    assertThat(consumer.reads()).isZero();

    // First delivery: the fake's first read returns null, simulating an unavailable QMID.
    IbmMqJakartaJmsListenerQmid.stamp(listener, null);
    assertThat(consumer.reads()).isEqualTo(EXPERIMENTAL_ATTRIBUTES ? 1 : 0);

    // Second delivery: the fake now returns the real QMID. Load-bearing assertion: the listener
    // is enriched on this later delivery even though its first read failed, proving the retry.
    IbmMqJakartaJmsListenerQmid.stamp(listener, null);

    if (EXPERIMENTAL_ATTRIBUTES) {
      assertThat(consumer.reads()).isEqualTo(2);
    } else {
      // Flag off: associate()/stamp() must no-op without ever touching the property context.
      assertThat(consumer.reads()).isZero();
    }
  }

  private static void assertQmid(SpanData span) {
    if (EXPERIMENTAL_ATTRIBUTES) {
      assertThat(span.getAttributes().get(QUEUE_MANAGER_ID)).isEqualTo(expectedQmid);
    } else {
      assertThat(span.getAttributes().get(QUEUE_MANAGER_ID)).isNull();
    }
  }

  private static void assertMessagingSystem(SpanData span) {
    if (EXPERIMENTAL_ATTRIBUTES) {
      assertThat(span.getAttributes().get(MESSAGING_SYSTEM)).isEqualTo("ibmmq");
    } else {
      // Flag off: the generic JMS instrumentation's "jms" value must be untouched.
      assertThat(span.getAttributes().get(MESSAGING_SYSTEM)).isEqualTo("jms");
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
    AtomicReference<SpanData> found = new AtomicReference<>();
    await()
        .untilAsserted(
            () -> {
              SpanData match =
                  testing.spans().stream()
                      .filter(span -> asList(span.getName().split(" ")).contains(operation))
                      .findFirst()
                      .orElse(null);
              assertThat(match)
                  .describedAs(
                      "span with operation token '%s' among: %s",
                      operation,
                      testing.spans().stream()
                          .map(s -> s.getKind() + "/" + s.getName())
                          .collect(toList()))
                  .isNotNull();
              found.set(match);
            });
    return found.get();
  }

  private static class CountingListener implements MessageListener {
    private final CountDownLatch latch;

    CountingListener(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void onMessage(Message message) {
      latch.countDown();
    }
  }

  /**
   * A minimal {@link JmsReadablePropertyContext} fake: returns {@code null} (simulating an
   * unavailable QMID) on its first read and the real broker's QMID on every read after, so a caller
   * that keeps retrying eventually gets the real value and one that gives up after the first
   * attempt never does.
   */
  private static class FlakyPropertyContext implements JmsReadablePropertyContext {
    private final String qmid;
    private final AtomicInteger reads = new AtomicInteger();

    FlakyPropertyContext(String qmid) {
      this.qmid = qmid;
    }

    int reads() {
      return reads.get();
    }

    @Override
    public String getStringProperty(String name) {
      return reads.getAndIncrement() == 0 ? null : qmid;
    }

    @Override
    public boolean propertyExists(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public char getCharProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBooleanProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte getByteProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public short getShortProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getIntProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getLongProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public float getFloatProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public double getDoubleProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getObjectProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getBytesProperty(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> getPropertyNames() {
      throw new UnsupportedOperationException();
    }
  }
}
