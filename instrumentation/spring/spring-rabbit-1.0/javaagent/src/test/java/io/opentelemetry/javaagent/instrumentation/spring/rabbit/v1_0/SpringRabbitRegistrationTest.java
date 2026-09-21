/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

class SpringRabbitRegistrationTest {

  private static final boolean springEnabled =
      Boolean.parseBoolean(
          System.getProperty("otel.instrumentation.spring-rabbit.enabled", "true"));

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void incomingMessageStartUsesExplicitParentWithUnrelatedCurrentContext() {
    assumeTrue(springEnabled);
    assertThat(Boolean.getBoolean("otel.javaagent.testing.fail-on-context-leak")).isTrue();

    AMQConnection rabbitConnection = mock(AMQConnection.class);
    when(rabbitConnection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(rabbitConnection.getPort()).thenReturn(5672);
    StubChannel channel = new StubChannel(rabbitConnection);

    MessageProperties properties = new MessageProperties();
    properties.setReceivedExchange("");
    properties.setReceivedRoutingKey("input");
    Message message = new Message(new byte[0], properties);
    AtomicReference<SpanContext> upstreamSpanContext = new AtomicReference<>();
    testing.runWithSpan(
        "upstream",
        () -> {
          upstreamSpanContext.set(Span.current().getSpanContext());
          testing
              .getOpenTelemetry()
              .getPropagators()
              .getTextMapPropagator()
              .inject(
                  Context.current(),
                  message,
                  (carrier, key, value) -> carrier.getMessageProperties().setHeader(key, value));
        });

    TestContainer container = new TestContainer();
    container.setMessageListener(
        (MessageListener)
            ignored -> assertThat(Span.current().getSpanContext().isValid()).isTrue());

    SpanContext callerSpan = Span.current().getSpanContext();
    testing.runWithSpan(
        "unrelated outer",
        () -> {
          SpanContext outerSpan = Span.current().getSpanContext();
          container.invoke(channel, message);
          assertThat(Span.current().getSpanContext()).isEqualTo(outerSpan);
        });

    assertThat(Span.current().getSpanContext()).isEqualTo(callerSpan);
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanName("upstream", "unrelated outer"),
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("upstream").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("unrelated outer").hasNoParent(),
                  span ->
                      span.hasName("process input")
                          .hasParent(trace.getSpan(0))
                          .hasLinksSatisfying(
                              links ->
                                  assertThat(links)
                                      .singleElement()
                                      .satisfies(
                                          link -> {
                                            assertThat(link.getSpanContext().getTraceId())
                                                .isEqualTo(upstreamSpanContext.get().getTraceId());
                                            assertThat(link.getSpanContext().getSpanId())
                                                .isEqualTo(upstreamSpanContext.get().getSpanId());
                                          }))
                          .satisfies(
                              data ->
                                  assertThat(data.getInstrumentationScopeInfo().getName())
                                      .isEqualTo("io.opentelemetry.spring-rabbit-1.0"))));
      return;
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("upstream").hasNoParent(),
                span ->
                    span.hasName("input process")
                        .hasParent(trace.getSpan(0))
                        .satisfies(
                            data ->
                                assertThat(data.getInstrumentationScopeInfo().getName())
                                    .isEqualTo("io.opentelemetry.spring-rabbit-1.0"))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("unrelated outer").hasNoParent()));
  }

  @Test
  void consumerTagCallbackCanRegisterAnIndependentConsumer() throws Exception {
    AMQConnection rabbitConnection = mock(AMQConnection.class);
    when(rabbitConnection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(rabbitConnection.getPort()).thenReturn(5672);
    StubChannel channel = new StubChannel(rabbitConnection);
    Connection connection = mock(Connection.class);
    when(connection.createChannel(anyBoolean())).thenReturn(channel);
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    when(connectionFactory.createConnection()).thenReturn(connection);

    TestContainer container = new TestContainer();
    container.setConnectionFactory(connectionFactory);
    container.setQueueNames("spring");
    container.setMessageListener(
        (MessageListener) message -> testing.runWithSpan("listener", () -> {}));
    container.setConsumerTagStrategy(
        queue -> {
          try {
            channel.basicConsume(
                "nested",
                true,
                new DefaultConsumer(channel) {
                  @Override
                  public void handleDelivery(
                      String consumerTag,
                      Envelope envelope,
                      AMQP.BasicProperties properties,
                      byte[] body) {
                    assertThat(Span.current().getSpanContext().isValid()).isTrue();
                    testing.runWithSpan("nested listener", () -> {});
                  }
                });
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
          return queue;
        });
    BlockingQueueConsumer consumer = container.createConsumer();
    consumer.start();
    testing.waitForTraces(4);
    testing.clearData();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(executor::shutdownNow);
    executor
        .submit(
            () -> {
              channel.deliver("nested");
              channel.deliver("spring");
              assertThat(Span.current().getSpanContext().isValid()).isFalse();
              return null;
            })
        .get();
    Message message = consumer.nextMessage(1000);
    assertThat(message).isNotNull();
    container.invoke(channel, message);

    if (!springEnabled) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("process nested"),
                  span -> span.hasName("nested listener").hasParent(trace.getSpan(0))),
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("process spring")),
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("listener").hasNoParent()));
      testing.waitAndAssertMetrics(
          "io.opentelemetry.rabbitmq-2.7",
          "messaging.client.consumed.messages",
          metrics ->
              metrics.satisfiesExactly(
                  metric ->
                      assertThat(
                              metric.getLongSumData().getPoints().stream()
                                  .mapToLong(point -> point.getValue())
                                  .sum())
                          .isEqualTo(2)));
      assertThat(testing.metrics())
          .noneMatch(
              metric ->
                  metric
                      .getInstrumentationScopeInfo()
                      .getName()
                      .equals("io.opentelemetry.spring-rabbit-1.0"));
      return;
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process nested" : "nested process")
                        .hasNoParent()
                        .satisfies(
                            data ->
                                assertThat(data.getInstrumentationScopeInfo().getName())
                                    .isEqualTo("io.opentelemetry.rabbitmq-2.7")),
                span -> span.hasName("nested listener").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process spring" : "spring process")
                        .satisfies(
                            data ->
                                assertThat(data.getInstrumentationScopeInfo().getName())
                                    .isEqualTo("io.opentelemetry.spring-rabbit-1.0")),
                span -> span.hasName("listener").hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertConsumedMessages("io.opentelemetry.rabbitmq-2.7", 1);
      assertConsumedMessages("io.opentelemetry.spring-rabbit-1.0", 1);
    }
    assertThat(Context.current()).isSameAs(Context.root());
  }

  @Test
  void reusedDelegateKeepsRegistrationAndCompletionLocal() throws Exception {
    AMQConnection connection = mock(AMQConnection.class);
    when(connection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(connection.getPort()).thenReturn(5672);
    StubChannel channel = new StubChannel(connection);
    AtomicInteger calls = new AtomicInteger();
    Consumer delegate =
        new DefaultConsumer(channel) {
          @Override
          public void handleDelivery(
              String tag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
              throws IOException {
            int invocation = calls.incrementAndGet();
            if (invocation == 1) {
              if (emitStableMessagingSemconv()) {
                SpanContext outer = Span.current().getSpanContext();
                channel.deliver("second");
                assertThat(Span.current().getSpanContext()).isEqualTo(outer);
              }
              throw new IOException("first registration");
            }
            testing.runWithSpan("callback", () -> {});
          }
        };
    channel.basicConsume("first", true, delegate);
    channel.basicConsume("second", true, delegate);
    testing.waitForTraces(2);
    testing.clearData();

    assertThatThrownBy(() -> channel.deliver("first"))
        .isInstanceOf(IOException.class)
        .hasMessage("first registration");
    Consumer cancelled = mock(Consumer.class);
    channel.basicConsume("cancelled", true, cancelled);
    channel.consumers.get("cancelled").handleCancelOk("cancelled");
    verify(cancelled).handleCancelOk("cancelled");
    channel.deliver("second");
    assertThat(Context.current()).isSameAs(Context.root());

    testing.waitAndAssertTraces(
        trace -> {
          if (emitStableMessagingSemconv()) {
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process first")
                        .hasException(new IOException("first registration")),
                span -> span.hasName("process second").hasParent(trace.getSpan(0)),
                span -> span.hasName("callback").hasParent(trace.getSpan(1)));
          } else {
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("first process")
                        .hasException(new IOException("first registration")));
          }
        },
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("Channel.basicConsume")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process second" : "second process")
                        .hasNoParent(),
                span -> span.hasName("callback").hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.rabbitmq-2.7",
          "messaging.client.consumed.messages",
          metrics ->
              metrics.satisfiesExactly(
                  metric ->
                      assertThat(
                              metric.getLongSumData().getPoints().stream()
                                  .mapToLong(point -> point.getValue())
                                  .sum())
                          .isEqualTo(3)));
    }
  }

  @Test
  void directContainerSelectsSpringAtRegistration() throws Exception {
    assumeTrue(springEnabled);
    AMQConnection rabbitConnection = mock(AMQConnection.class);
    when(rabbitConnection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(rabbitConnection.getPort()).thenReturn(5672);
    StubChannel channel = new StubChannel(rabbitConnection);
    Connection connection = mock(Connection.class);
    when(connection.isOpen()).thenReturn(true);
    when(connection.createChannel(anyBoolean())).thenReturn(channel);
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    when(connectionFactory.createConnection()).thenReturn(connection);
    DirectMessageListenerContainer container =
        new DirectMessageListenerContainer(connectionFactory);
    container.setQueueNames("direct");
    container.setAcknowledgeMode(AcknowledgeMode.NONE);
    container.setMessageListener(
        (MessageListener) message -> testing.runWithSpan("listener", () -> {}));
    cleanup.deferCleanup(container::stop);
    container.start();
    await().until(() -> channel.consumers.containsKey("direct"));
    testing.waitForTraces(3);
    testing.clearData();

    channel.deliver("direct");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process direct" : "direct process")
                        .satisfies(
                            data ->
                                assertThat(data.getInstrumentationScopeInfo().getName())
                                    .isEqualTo("io.opentelemetry.spring-rabbit-1.0")),
                span -> span.hasName("listener").hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertConsumedMessages("io.opentelemetry.spring-rabbit-1.0", 1);
    }
  }

  @Test
  void consumerBatchRegistrationKeepsRabbitFallback() throws Exception {
    assumeTrue(testLatestDeps() && springEnabled);
    AMQConnection rabbitConnection = mock(AMQConnection.class);
    when(rabbitConnection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(rabbitConnection.getPort()).thenReturn(5672);
    StubChannel channel = new StubChannel(rabbitConnection);
    Connection connection = mock(Connection.class);
    when(connection.createChannel(anyBoolean())).thenReturn(channel);
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    when(connectionFactory.createConnection()).thenReturn(connection);
    TestContainer container = new TestContainer();
    container.setConnectionFactory(connectionFactory);
    container.setQueueNames("batch");
    container.setMessageListener(
        (MessageListener) message -> testing.runWithSpan("listener", () -> {}));
    SimpleMessageListenerContainer.class
        .getMethod("setConsumerBatchEnabled", boolean.class)
        .invoke(container, true);
    BlockingQueueConsumer consumer = container.createConsumer();
    consumer.start();
    testing.waitForTraces(3);
    testing.clearData();
    channel.deliver("batch");
    container.invoke(channel, consumer.nextMessage(1000));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process batch" : "batch process")
                        .satisfies(
                            data ->
                                assertThat(data.getInstrumentationScopeInfo().getName())
                                    .isEqualTo("io.opentelemetry.rabbitmq-2.7"))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("listener").hasNoParent()));
    if (emitStableMessagingSemconv()) {
      assertConsumedMessages("io.opentelemetry.rabbitmq-2.7", 1);
    }
  }

  private static void assertConsumedMessages(String scope, long count) {
    testing.waitAndAssertMetrics(
        scope,
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum -> sum.hasPointsSatisfying(point -> point.hasValue(count)))));
  }

  private static class TestContainer extends SimpleMessageListenerContainer {
    BlockingQueueConsumer createConsumer() {
      return createBlockingQueueConsumer();
    }

    void invoke(Channel channel, Message message) {
      invokeListener(channel, message);
    }
  }

  private static class StubChannel extends ChannelN {
    private final Map<String, Consumer> consumers = new ConcurrentHashMap<>();

    StubChannel(AMQConnection connection) {
      super(connection, 1, mock(ConsumerWorkService.class));
    }

    @Override
    public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException {}

    @Override
    public AMQImpl.Queue.DeclareOk queueDeclarePassive(String queue) throws IOException {
      return new AMQImpl.Queue.DeclareOk(queue, 0, 0);
    }

    @Override
    public void basicCancel(String consumerTag) throws IOException {
      consumers.remove(consumerTag).handleCancelOk(consumerTag);
    }

    @Override
    public void close() throws IOException {}

    @Override
    public String basicConsume(
        String queue,
        boolean autoAck,
        String consumerTag,
        boolean noLocal,
        boolean exclusive,
        Map<String, Object> arguments,
        Consumer consumer)
        throws IOException {
      consumers.put(queue, consumer);
      consumer.handleConsumeOk(queue);
      return queue;
    }

    void deliver(String queue) throws IOException {
      consumers
          .get(queue)
          .handleDelivery(
              queue,
              new Envelope(1, false, "", queue),
              new AMQP.BasicProperties.Builder().build(),
              new byte[0]);
    }
  }
}
