/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertNoMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertProcessMetrics;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.interceptor.GlobalChannelInterceptorWrapper;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

class MessageProducerSupportInstrumentationTest {

  private static final String SPRING_RABBIT_INSTRUMENTATION_NAME =
      "io.opentelemetry.spring-rabbit-1.0";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static AnnotationConfigApplicationContext applicationContext;
  private static ChannelInterceptor interceptor;

  @BeforeAll
  static void setUpAll() {
    applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.refresh();
    interceptor =
        applicationContext
            .getBean("otelGlobalChannelInterceptor", GlobalChannelInterceptorWrapper.class)
            .getChannelInterceptor();
  }

  @AfterAll
  static void tearDownAll() {
    applicationContext.close();
  }

  @Test
  void endpointHandoffKeepsLowerProcessingOwner() {
    assumeRabbitInstrumentationEnabled();
    DirectChannel channel = newChannel();
    TestMessageProducer producer = new TestMessageProducer(channel);
    Message message = newRawMessage();

    producer.send(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(lowerProcessSpanName()).hasKind(SpanKind.CONSUMER)));
    assertNoMetrics(testing);
  }

  @Test
  void endpointHandoffDoesNotSuppressDistinctNestedMessage() {
    assumeRabbitInstrumentationEnabled();
    DirectChannel nestedChannel = newChannel("nested");
    DirectChannel inputChannel =
        newChannel(
            "input", message -> nestedChannel.send(MessageBuilder.withPayload("nested").build()));
    TestMessageProducer producer = new TestMessageProducer(inputChannel);
    Message message = newRawMessage();

    producer.send(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(lowerProcessSpanName()).hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process nested" : "nested process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "nested", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void nestedEndpointHandoffKeepsItsOwnLowerProcessingOwner() {
    assumeRabbitInstrumentationEnabled();
    DirectChannel innerChannel = newChannel("inner");
    TestMessageProducer innerProducer = new TestMessageProducer(innerChannel);
    DirectChannel outerChannel =
        newChannel("outer", message -> innerProducer.send(newRawMessage()));

    outerChannel.send(MessageBuilder.withPayload("outer").build());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process outer" : "outer process")
                        .hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName(lowerProcessSpanName())
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "outer", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void unownedNestedEndpointHandoffMasksOuterOwner() {
    assumeRabbitInstrumentationEnabled();
    DirectChannel innerChannel = newChannel("inner");
    TestMessageProducer innerProducer = new TestMessageProducer(innerChannel);
    DirectChannel outerChannel =
        newChannel("outer", message -> innerProducer.sendWithoutContainer(newRawMessage()));
    TestMessageProducer outerProducer = new TestMessageProducer(outerChannel);
    Message outerMessage = newRawMessage();

    outerProducer.send(outerMessage);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(lowerProcessSpanName()).hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process inner" : "inner process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "inner", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void endpointHandoffTracesEachExecutorHandler() {
    assumeRabbitInstrumentationEnabled();
    ExecutorSubscribableChannel inputChannel = new ExecutorSubscribableChannel(Runnable::run);
    inputChannel.setBeanName("input");
    inputChannel.addInterceptor(interceptor);
    inputChannel.subscribe(message -> {});
    inputChannel.subscribe(message -> {});
    TestMessageProducer producer = new TestMessageProducer(inputChannel);
    Message message = newRawMessage();

    producer.send(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(lowerProcessSpanName()).hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process input" : "input process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process input" : "input process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "input", false, 2);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void endpointHandoffUsesSpringIntegrationFallbackWithoutLowerProcessing() {
    assumeRabbitInstrumentationDisabled();
    DirectChannel channel = newChannel();
    TestMessageProducer producer = new TestMessageProducer(channel);

    producer.send(newRawMessage());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process input" : "input process")
                        .hasKind(SpanKind.CONSUMER)));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "input", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void endpointHandoffRestoresPreviousState() {
    assumeRabbitInstrumentationEnabled();
    DirectChannel channel = newChannel();
    TestMessageProducer producer = new TestMessageProducer(channel);
    Message ownedMessage = newRawMessage();

    producer.send(ownedMessage);

    producer.sendWithoutContainer(newRawMessage());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(lowerProcessSpanName()).hasKind(SpanKind.CONSUMER)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process input" : "input process")
                        .hasKind(SpanKind.CONSUMER)));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "input", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void endpointHandoffUsesSpringIntegrationFallbackUnderUnrelatedConsumerProcess() {
    assumeRabbitInstrumentationDisabled();
    DirectChannel channel = newChannel();
    TestMessageProducer producer = new TestMessageProducer(channel);
    Message message = newRawMessage();
    Instrumenter<Message, Void> unrelatedProcessInstrumenter = newUnrelatedProcessInstrumenter();

    Context context = unrelatedProcessInstrumenter.start(Context.current(), message);
    context = SpanKey.CONSUMER_PROCESS.storeInContext(context, Span.fromContext(context));
    try (Scope ignored = context.makeCurrent()) {
      producer.send(message);
    }
    unrelatedProcessInstrumenter.end(context, message, null, null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("lower process").hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process input" : "input process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertUnrelatedProcessMetrics();
      assertProcessMetrics(testing, "input", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void endpointHandoffDoesNotTransferOwnershipToErrorMessage() {
    assumeRabbitInstrumentationEnabled();
    DirectChannel errorChannel = newChannel("error");
    DirectChannel inputChannel =
        newChannel(
            "input",
            message -> {
              throw new IllegalStateException("test");
            });
    TestMessageProducer producer = new TestMessageProducer(inputChannel);
    producer.setErrorChannel(errorChannel);
    Message message = newRawMessage();

    producer.send(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(lowerProcessSpanName()).hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process error" : "error process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "error", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void retainedAdapterRetryKeepsLowerProcessingOwner() {
    assumeRabbitInstrumentationEnabled();
    assumeTrue(testLatestDeps());
    AtomicInteger attempts = new AtomicInteger();
    DirectChannel inputChannel =
        newChannel(
            "input",
            message -> {
              if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("first attempt");
              }
            });
    TestMessageProducer producer = new TestMessageProducer(inputChannel, true);
    Message message = newRawMessage();

    producer.send(message);

    assertThat(attempts).hasValue(2);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(lowerProcessSpanName()).hasKind(SpanKind.CONSUMER)));
    assertNoMetrics(testing);
    assertSpringRabbitProcessMetrics();
  }

  private static DirectChannel newChannel() {
    return newChannel("input", message -> {});
  }

  private static DirectChannel newChannel(String name) {
    return newChannel(name, message -> {});
  }

  private static DirectChannel newChannel(String name, MessageHandler handler) {
    DirectChannel channel = new DirectChannel();
    channel.setBeanName(name);
    channel.addInterceptor(interceptor);
    channel.subscribe(handler);
    return channel;
  }

  private static Instrumenter<Message, Void> newUnrelatedProcessInstrumenter() {
    return Instrumenter.<Message, Void>builder(
            GlobalOpenTelemetry.get(), "test-lower-messaging-process", unused -> "lower process")
        .addAttributesExtractor(AttributesExtractor.constant(MESSAGING_SYSTEM, "test"))
        .addAttributesExtractor(AttributesExtractor.constant(MESSAGING_OPERATION_NAME, "process"))
        .addAttributesExtractor(AttributesExtractor.constant(MESSAGING_OPERATION_TYPE, "process"))
        .addAttributesExtractor(AttributesExtractor.constant(MESSAGING_DESTINATION_NAME, "input"))
        .addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages())
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static void assertUnrelatedProcessMetrics() {
    testing.waitAndAssertMetrics(
        "test-lower-messaging-process",
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, "test"),
                                                equalTo(MESSAGING_DESTINATION_NAME, "input"))))));
  }

  private static void assertSpringRabbitProcessMetrics() {
    if (!emitStableMessagingSemconv()) {
      return;
    }
    testing.waitAndAssertMetrics(
        SPRING_RABBIT_INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(point -> point.hasCount(1)))));
  }

  private static Message newRawMessage() {
    MessageProperties messageProperties = new MessageProperties();
    messageProperties.setConsumerQueue("input");
    messageProperties.setReceivedRoutingKey("input");
    return new Message("test".getBytes(UTF_8), messageProperties);
  }

  private static String lowerProcessSpanName() {
    return emitStableMessagingSemconv() ? "process input" : "input process";
  }

  private static void assumeRabbitInstrumentationEnabled() {
    assumeTrue(Boolean.getBoolean("springIntegrationRabbitHandoffTest"));
  }

  private static void assumeRabbitInstrumentationDisabled() {
    assumeFalse(Boolean.getBoolean("springIntegrationRabbitHandoffTest"));
  }

  private static final class TestMessageProducer extends AmqpInboundChannelAdapter {
    private final TestContainer container;

    private TestMessageProducer(MessageChannel channel) {
      this(new TestContainer(), channel, false);
    }

    private TestMessageProducer(MessageChannel channel, boolean retry) {
      this(new TestContainer(), channel, retry);
    }

    private TestMessageProducer(TestContainer container, MessageChannel channel, boolean retry) {
      super(container);
      this.container = container;
      setBeanName("testProducer");
      setOutputChannel(channel);
      setShouldTrack(true);
      if (retry) {
        configureRetry();
      }
      afterPropertiesSet();
    }

    private void send(Message message) {
      container.invoke(message);
    }

    private void sendWithoutContainer(Message message) {
      try {
        Object listener = container.getMessageListener();
        listener
            .getClass()
            .getMethod("onMessage", Message.class, Channel.class)
            .invoke(listener, message, newChannelMock());
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
          throw (Error) cause;
        }
        throw new IllegalStateException(cause);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }

    private void configureRetry() {
      try {
        Class<?> retryTemplateClass =
            Class.forName("org.springframework.retry.support.RetryTemplate");
        AmqpInboundChannelAdapter.class
            .getMethod("setRetryTemplate", retryTemplateClass)
            .invoke(this, retryTemplateClass.getConstructor().newInstance());
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static final class TestContainer extends SimpleMessageListenerContainer {
    private TestContainer() {
      setConnectionFactory(mock(ConnectionFactory.class));
    }

    @Override
    protected void doInitialize() {}

    private void invoke(Message message) {
      invokeListener(newChannelMock(), message);
    }
  }

  private static Channel newChannelMock() {
    Connection connection = mock(Connection.class);
    when(connection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(connection.getPort()).thenReturn(5672);
    Channel channel = mock(Channel.class);
    when(channel.getConnection()).thenReturn(connection);
    return channel;
  }
}
