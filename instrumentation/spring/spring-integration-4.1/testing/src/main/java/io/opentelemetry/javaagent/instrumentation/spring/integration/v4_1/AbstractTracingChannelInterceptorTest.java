/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.AbstractSpringIntegrationTracingTest.verifyCorrectSpanWasPropagated;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Covers what the interceptor must preserve while rebuilding a message to carry trace context: the
 * payload, the existing headers, the message id, the message type, and the {@link
 * MessageHeaderAccessor} binding, which Spring components such as {@code
 * StompBrokerRelayMessageHandler} recover via {@link MessageHeaderAccessor#getAccessor(Message,
 * Class)}.
 */
abstract class AbstractTracingChannelInterceptorTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final InstrumentationExtension testing;

  private final Class<?> additionalContextClass;

  private ConfigurableApplicationContext applicationContext;

  AbstractTracingChannelInterceptorTest(
      InstrumentationExtension testing, Class<?> additionalContextClass) {
    this.testing = testing;
    this.additionalContextClass = additionalContextClass;
  }

  @BeforeEach
  void setUp() {
    List<Class<?>> contextClasses = new ArrayList<>();
    contextClasses.add(AbstractSpringIntegrationTracingTest.MessageChannelsConfig.class);
    if (additionalContextClass != null) {
      contextClasses.add(additionalContextClass);
    }
    SpringApplication springApplication =
        new SpringApplication(contextClasses.toArray(new Class<?>[0]));
    springApplication.setDefaultProperties(
        singletonMap("spring.main.web-application-type", "none"));
    applicationContext = springApplication.run();
    cleanup.deferCleanup(applicationContext);
  }

  @ParameterizedTest
  @ValueSource(strings = {"directChannel", "executorChannel"})
  void preservesHeaderAccessorOfImmutableMessage(String channelName) {
    // messages produced by SimpMessagingTemplate carry immutable headers, so the interceptor has to
    // take a mutable copy of the accessor rather than mutating the message in place
    TestHeaderAccessor accessor = new TestHeaderAccessor();
    accessor.setHeader("testKey", "testValue");
    Message<?> message = MessageBuilder.createMessage("payload", accessor.getMessageHeaders());

    Message<?> received = sendAndCapture(channelName, message);

    assertThat(received.getPayload()).isEqualTo("payload");
    assertThat(received.getHeaders()).containsEntry("testKey", "testValue");
    assertThat(MessageHeaderAccessor.getAccessor(received, TestHeaderAccessor.class)).isNotNull();
    // a rebuilt message still needs an id, and must not go on sharing mutable headers with the
    // accessor the interceptor injected into
    assertThat(received.getHeaders().getId()).isNotNull();
    assertThat(MessageHeaderAccessor.getAccessor(received, MessageHeaderAccessor.class).isMutable())
        .isFalse();

    assertContextWasPropagated(received);
  }

  @Test
  void preservesHeaderAccessorOfMutableMessage() {
    TestHeaderAccessor accessor = new TestHeaderAccessor();
    accessor.setLeaveMutable(true);
    accessor.setHeader("testKey", "testValue");

    Message<?> received =
        sendAndCapture(
            "directChannel", MessageBuilder.createMessage("payload", accessor.getMessageHeaders()));

    assertThat(received.getHeaders()).containsEntry("testKey", "testValue");
    assertThat(MessageHeaderAccessor.getAccessor(received, TestHeaderAccessor.class)).isNotNull();
    // sealing the accessor here would seal the caller's message too
    assertThat(accessor.isMutable()).isTrue();

    assertContextWasPropagated(received);
  }

  @Test
  void doesNotShareHeadersBetweenSends() {
    TestHeaderAccessor accessor = new TestHeaderAccessor();
    Message<?> message = MessageBuilder.createMessage("payload", accessor.getMessageHeaders());

    Message<?> first = sendAndCapture("directChannel", message);
    Object firstTraceParent = first.getHeaders().get("traceparent");

    Message<?> second = sendAndCapture("directChannel", first);

    assertThat(second.getHeaders()).isNotSameAs(first.getHeaders());
    assertThat(first.getHeaders().get("traceparent")).isEqualTo(firstTraceParent);
  }

  @Test
  void preservesStompHeaderAccessor() {
    // the shape SimpMessagingTemplate produces and StompBrokerRelayMessageHandler consumes
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.setDestination("/topic/test");

    Message<?> received =
        sendAndCapture(
            "directChannel", MessageBuilder.createMessage("payload", accessor.getMessageHeaders()));

    StompHeaderAccessor recovered =
        MessageHeaderAccessor.getAccessor(received, StompHeaderAccessor.class);
    assertThat(recovered).isNotNull();
    assertThat(recovered.getDestination()).isEqualTo("/topic/test");
    // a STOMP frame only carries native headers, so the context has to reach them too
    assertThat(recovered.getNativeHeader("traceparent")).isNotNull();

    assertContextWasPropagated(received);
  }

  @Test
  void preservesErrorMessage() {
    // ErrorMessage keeps the MessageBuilder path so that its originalMessage survives
    IllegalStateException payload = new IllegalStateException("boom");
    TestHeaderAccessor accessor = new TestHeaderAccessor();

    Message<?> received =
        sendAndCapture(
            "directChannel", MessageBuilder.createMessage(payload, accessor.getMessageHeaders()));

    assertThat(received).isInstanceOf(ErrorMessage.class);
    assertThat(received.getPayload()).isSameAs(payload);
    assertThat(received.getHeaders().getId()).isNotNull();

    assertContextWasPropagated(received);
  }

  @Test
  void preservesErrorMessageOriginalMessage() throws ReflectiveOperationException {
    // MessageBuilder only carries originalMessage over into a rebuilt ErrorMessage from 5.3 on;
    // reflection is needed either way because neither the constructor that populates it nor
    // getOriginalMessage() exists in 4.1, which is the version this module compiles against
    assumeTrue(testLatestDeps());
    Constructor<ErrorMessage> constructor =
        ErrorMessage.class.getConstructor(Throwable.class, MessageHeaders.class, Message.class);
    Message<?> original = new GenericMessage<>("original");
    TestHeaderAccessor accessor = new TestHeaderAccessor();
    Message<?> errorMessage =
        constructor.newInstance(
            new IllegalStateException("boom"), accessor.getMessageHeaders(), original);

    Message<?> received = sendAndCapture("directChannel", errorMessage);

    assertThat(ErrorMessage.class.getMethod("getOriginalMessage").invoke(received))
        .isSameAs(original);
  }

  @Test
  void preservesPayloadAndHeadersOfPlainMessage() {
    Message<?> received =
        sendAndCapture(
            "directChannel",
            MessageBuilder.withPayload("payload").setHeader("testKey", "testValue").build());

    assertThat(received.getPayload()).isEqualTo("payload");
    assertThat(received.getHeaders()).containsEntry("testKey", "testValue");
    assertThat(received.getHeaders().getId()).isNotNull();

    assertContextWasPropagated(received);
  }

  @Test
  void injectsTraceContextIntoPlainMessage() {
    Message<?> received = sendAndCapture("directChannel", new GenericMessage<>("payload"));

    assertThat(received.getHeaders()).containsKey("traceparent");

    assertContextWasPropagated(received);
  }

  /** Sends {@code message} through an intercepted channel and returns what the handler received. */
  private Message<?> sendAndCapture(String channelName, Message<?> message) {
    SubscribableChannel channel =
        applicationContext.getBean(channelName, SubscribableChannel.class);
    CapturingMessageHandler messageHandler = new CapturingMessageHandler();
    channel.subscribe(messageHandler);
    try {
      channel.send(message);
      return messageHandler.join();
    } finally {
      channel.unsubscribe(messageHandler);
    }
  }

  /** Asserts that the delivered message carries the trace context of the interceptor's span. */
  private void assertContextWasPropagated(Message<?> received) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasKind(SpanKind.CONSUMER);
                  verifyCorrectSpanWasPropagated(received, trace.getSpan(0));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));
  }

  /**
   * Stands in for accessor subclasses such as {@code SimpMessageHeaderAccessor}, which override
   * {@link MessageHeaderAccessor#createAccessor(Message)} so that the subclass survives when a
   * mutable copy is taken of an immutable message.
   */
  static class TestHeaderAccessor extends MessageHeaderAccessor {

    TestHeaderAccessor() {
      super();
    }

    TestHeaderAccessor(Message<?> message) {
      super(message);
    }

    @Override
    protected MessageHeaderAccessor createAccessor(Message<?> message) {
      return new TestHeaderAccessor(message);
    }
  }
}
