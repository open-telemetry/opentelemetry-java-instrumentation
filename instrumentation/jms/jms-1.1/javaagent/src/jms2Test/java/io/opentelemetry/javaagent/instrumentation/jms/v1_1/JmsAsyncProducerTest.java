/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertCounter;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class JmsAsyncProducerTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final ContextKey<String> KEY = ContextKey.named("jms-test");
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @CsvSource({"0,false", "1,false", "2,false", "3,false", "0,true", "1,true", "2,true", "3,true"})
  void deferredCompletion(int overload, boolean failed) throws Exception {
    TestProducer producer = new TestProducer();
    Message message = mock(Message.class);
    JMSException failure = new JMSException("send failed");
    AtomicInteger callbacks = new AtomicInteger();
    AtomicReference<SpanContext> callbackContext = new AtomicReference<>();
    CompletionListener listener =
        new CompletionListener() {
          @Override
          public void onCompletion(Message completed) {
            assertThat(completed).isSameAs(message);
            assertThat(Context.current().get(KEY)).isEqualTo("sending");
            callbackContext.set(Span.current().getSpanContext());
            callbacks.incrementAndGet();
          }

          @Override
          public void onException(Message completed, Exception exception) {
            assertThat(exception).isSameAs(failure);
            onCompletion(completed);
          }
        };
    Context original = Context.current();
    try (Scope ignored = original.with(KEY, "sending").makeCurrent()) {
      Context callingContext = Context.current();
      send(producer, message, listener, overload);
      assertContext(callingContext);
      assertThat(testing.spans()).isEmpty();
      assertThat(testing.metrics()).noneMatch(metric -> metric.getName().startsWith("messaging."));
    }
    assertContext(original);

    try (Scope ignored = original.with(KEY, "callback-thread").makeCurrent()) {
      Context callbackThread = Context.current();
      for (int i = 0; i < 2; i++) {
        if (failed) {
          producer.listener.onException(message, failure);
        } else {
          producer.listener.onCompletion(message);
        }
        assertContext(callbackThread);
      }
      if (failed) {
        producer.listener.onCompletion(message);
      } else {
        producer.listener.onException(message, failure);
      }
      assertContext(callbackThread);
    }
    assertThat(callbacks.get()).isEqualTo(3);
    assertSend(failed ? failure : null);
    assertThat(callbackContext.get().getTraceId())
        .isEqualTo(Span.fromContext(original).getSpanContext().getTraceId());
    assertThat(callbackContext.get().getSpanId())
        .isEqualTo(Span.fromContext(original).getSpanContext().getSpanId());
  }

  @ParameterizedTest
  @MethodSource("crossThreadCompletionCases")
  void crossThreadCompletion(boolean failed, boolean overlapping) throws Exception {
    CountDownLatch sendStarted = new CountDownLatch(1);
    CountDownLatch callbackCompleted = new CountDownLatch(1);
    TestProducer producer =
        new TestProducer() {
          @Override
          public void send(Message message, CompletionListener completionListener)
              throws JMSException {
            super.send(message, completionListener);
            if (overlapping) {
              Context sendingContext = Context.current();
              assertThat(Span.fromContext(sendingContext).getSpanContext().isValid()).isTrue();
              sendStarted.countDown();
              try {
                assertThat(callbackCompleted.await(10, SECONDS)).isTrue();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
              }
              assertContext(sendingContext);
            }
          }
        };
    ExecutorService executor = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(executor::shutdownNow);
    Message message = mock(Message.class);
    JMSException failure = new JMSException("send failed");
    AtomicInteger callbacks = new AtomicInteger();
    Thread sender = Thread.currentThread();
    Context original = Context.current();
    try (Scope ignored = original.with(KEY, "sending").makeCurrent()) {
      Context callingContext = Context.current();
      CompletionListener listener =
          new CompletionListener() {
            @Override
            public void onCompletion(Message completed) {
              assertThat(completed).isSameAs(message);
              assertContext(callingContext);
              callbacks.incrementAndGet();
            }

            @Override
            public void onException(Message completed, Exception exception) {
              assertThat(exception).isSameAs(failure);
              onCompletion(completed);
            }
          };
      Future<?> callback =
          executor.submit(
              () -> {
                try {
                  Context workerOriginal = Context.current();
                  Context workerContext =
                      Context.root()
                          .with(KEY, "callback-thread")
                          .with(
                              Span.wrap(
                                  SpanContext.create(
                                      "12345678123456781234567812345678",
                                      "1234567812345678",
                                      TraceFlags.getSampled(),
                                      TraceState.getDefault())));
                  try (Scope workerScope = workerContext.makeCurrent()) {
                    assertThat(Thread.currentThread()).isNotSameAs(sender);
                    assertThat(sendStarted.await(10, SECONDS)).isTrue();
                    if (failed) {
                      producer.listener.onException(message, failure);
                    } else {
                      producer.listener.onCompletion(message);
                    }
                    assertContext(workerContext);
                    if (failed) {
                      producer.listener.onCompletion(message);
                    } else {
                      producer.listener.onException(message, failure);
                    }
                    assertContext(workerContext);
                  }
                  assertContext(workerOriginal);
                  return null;
                } finally {
                  callbackCompleted.countDown();
                }
              });
      producer.send(message, listener);
      assertContext(callingContext);
      if (!overlapping) {
        assertThat(testing.spans()).isEmpty();
        assertThat(testing.metrics())
            .noneMatch(metric -> metric.getName().startsWith("messaging."));
      }
      sendStarted.countDown();
      callback.get(10, SECONDS);
      assertContext(callingContext);
    }
    assertContext(original);
    assertThat(callbacks.get()).isEqualTo(2);
    assertSend(failed ? failure : null);
  }

  private static Stream<Arguments> crossThreadCompletionCases() {
    return Stream.of(
        argumentSet("success after send returns", false, false),
        argumentSet("failure after send returns", true, false),
        argumentSet("success while send scope is open", false, true),
        argumentSet("failure while send scope is open", true, true));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void callbackCanStartAnotherSend(int overload) throws Exception {
    TestProducer first = new TestProducer();
    TestProducer second = new TestProducer();
    Message secondMessage = mock(Message.class);
    CompletionListener listener =
        new CompletionListener() {
          @Override
          public void onCompletion(Message completed) {
            try {
              second.send(secondMessage);
            } catch (JMSException e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public void onException(Message completed, Exception exception) {
            throw new AssertionError(exception);
          }
        };
    Message message = mock(Message.class);
    Context original = Context.current();
    send(first, message, listener, overload);
    first.listener.onCompletion(message);
    assertContext(original);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send queue").hasKind(SpanKind.PRODUCER).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send queue").hasKind(SpanKind.PRODUCER).hasNoParent()));
    assertSendMetrics(null, 2);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void synchronousThrowEndsOnce(int overload) throws Exception {
    TestProducer producer = new TestProducer();
    producer.failure = new JMSException("synchronous failure");
    Message message = mock(Message.class);
    CompletionListener listener = mock(CompletionListener.class);
    Context original = Context.current();
    assertThatThrownBy(() -> send(producer, message, listener, overload))
        .isSameAs(producer.failure);
    assertContext(original);
    producer.listener.onCompletion(message);
    producer.listener.onException(message, new JMSException("late failure"));
    assertSend(producer.failure);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void callbackThrowIsPreservedAndContextRestored(boolean failed) throws Exception {
    TestProducer producer = new TestProducer();
    Message message = mock(Message.class);
    IllegalStateException callbackFailure = new IllegalStateException("callback failure");
    Context callbackParent = Context.current();
    CompletionListener listener =
        new CompletionListener() {
          @Override
          public void onCompletion(Message completed) {
            assertContext(callbackParent);
            throw callbackFailure;
          }

          @Override
          public void onException(Message completed, Exception exception) {
            onCompletion(completed);
          }
        };
    producer.send(message, listener);
    Context original = Context.current();
    JMSException sendFailure = new JMSException("send failed");
    assertThatThrownBy(
            () -> {
              if (failed) {
                producer.listener.onException(message, sendFailure);
              } else {
                producer.listener.onCompletion(message);
              }
            })
        .isSameAs(callbackFailure);
    assertContext(original);
    assertSend(failed ? sendFailure : null);
  }

  @ParameterizedTest
  @CsvSource({"0,false", "1,false", "2,false", "3,false", "0,true", "1,true", "2,true", "3,true"})
  void synchronousSendStillEndsOnReturn(int overload, boolean failed) throws Exception {
    TestProducer producer = new TestProducer();
    if (failed) {
      producer.failure = new JMSException("synchronous failure");
    }
    Context original = Context.current();
    if (failed) {
      assertThatThrownBy(() -> sendSynchronously(producer, mock(Message.class), overload))
          .isSameAs(producer.failure);
    } else {
      sendSynchronously(producer, mock(Message.class), overload);
    }
    assertContext(original);
    assertSend(producer.failure);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void inlineCallbackOwnsCompletionEvenWhenSendThrows(boolean failed) throws Exception {
    TestProducer producer = new TestProducer();
    producer.completeInline = true;
    if (failed) {
      producer.failure = new JMSException("throw after callback");
    }
    CompletionListener listener = mock(CompletionListener.class);
    Context original = Context.current();
    if (failed) {
      assertThatThrownBy(() -> producer.send(mock(Message.class), listener))
          .isSameAs(producer.failure);
    } else {
      producer.send(mock(Message.class), listener);
    }
    assertContext(original);
    assertSend(null);
  }

  private static void sendSynchronously(TestProducer producer, Message message, int overload)
      throws JMSException {
    switch (overload) {
      case 0:
        producer.send(message);
        break;
      case 1:
        producer.send(message, 2, 4, 0);
        break;
      case 2:
        producer.send(producer.destination, message);
        break;
      default:
        producer.send(producer.destination, message, 2, 4, 0);
    }
  }

  private static void send(
      TestProducer producer, Message message, CompletionListener listener, int overload)
      throws JMSException {
    switch (overload) {
      case 0:
        producer.send(message, listener);
        break;
      case 1:
        producer.send(message, 2, 4, 0, listener);
        break;
      case 2:
        producer.send(producer.destination, message, listener);
        break;
      default:
        producer.send(producer.destination, message, 2, 4, 0, listener);
    }
  }

  private static void assertSend(Exception failure) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("send queue")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasStatusSatisfying(
                            status ->
                                status.hasCode(
                                    failure == null ? StatusCode.UNSET : StatusCode.ERROR))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(MESSAGING_DESTINATION_NAME, "queue"),
                            equalTo(
                                ERROR_TYPE,
                                failure == null ? null : failure.getClass().getName()))));
    assertSendMetrics(failure, 1);
  }

  private static void assertSendMetrics(Exception failure, long count) {
    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "jms")
            .put(MESSAGING_OPERATION_NAME, "send")
            .put(MESSAGING_OPERATION_TYPE, "send")
            .put(MESSAGING_DESTINATION_NAME, "queue")
            .put(ERROR_TYPE, failure == null ? null : failure.getClass().getName())
            .build();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.operation.duration",
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metric ->
                        assertThat(metric.getHistogramData().getPoints())
                            .singleElement()
                            .satisfies(
                                point -> {
                                  assertThat(point.getCount()).isEqualTo(count);
                                  assertThat(point.getAttributes()).isEqualTo(attributes);
                                })));
    assertCounter(
        testing,
        INSTRUMENTATION_NAME,
        "messaging.client.sent.messages",
        count,
        attributes.toBuilder().remove(MESSAGING_OPERATION_TYPE).build());
  }

  private static void assertContext(Context expected) {
    assertThat(Span.current().getSpanContext())
        .isEqualTo(Span.fromContext(expected).getSpanContext());
    assertThat(Context.current().get(KEY)).isEqualTo(expected.get(KEY));
  }

  public static class TestProducer implements MessageProducer {
    final Queue destination = mock(Queue.class);
    CompletionListener listener;
    JMSException failure;
    boolean completeInline;

    TestProducer() throws JMSException {
      when(destination.getQueueName()).thenReturn("queue");
    }

    @Override
    public void send(Message message, CompletionListener completionListener) throws JMSException {
      listener = completionListener;
      if (completeInline) {
        listener.onCompletion(message);
      }
      if (failure != null) {
        throw failure;
      }
    }

    @Override
    public void send(
        Message message, int mode, int priority, long ttl, CompletionListener completionListener)
        throws JMSException {
      send(message, completionListener);
    }

    @Override
    public void send(
        Destination destination, Message message, CompletionListener completionListener)
        throws JMSException {
      send(message, completionListener);
    }

    @Override
    public void send(
        Destination destination,
        Message message,
        int mode,
        int priority,
        long ttl,
        CompletionListener completionListener)
        throws JMSException {
      send(message, completionListener);
    }

    @Override
    public void send(Message message) throws JMSException {
      assertThat(Span.current().getSpanContext().isValid()).isTrue();
      if (failure != null) {
        throw failure;
      }
    }

    @Override
    public void send(Message message, int mode, int priority, long ttl) throws JMSException {
      send(message);
    }

    @Override
    public void send(Destination destination, Message message) throws JMSException {
      send(message);
    }

    @Override
    public void send(Destination destination, Message message, int mode, int priority, long ttl)
        throws JMSException {
      send(message);
    }

    @Override
    public Destination getDestination() {
      return destination;
    }

    @Override
    public void close() {}

    @Override
    public void setDisableMessageID(boolean value) {}

    @Override
    public boolean getDisableMessageID() {
      return false;
    }

    @Override
    public void setDisableMessageTimestamp(boolean value) {}

    @Override
    public boolean getDisableMessageTimestamp() {
      return false;
    }

    @Override
    public void setDeliveryMode(int value) {}

    @Override
    public int getDeliveryMode() {
      return 2;
    }

    @Override
    public void setPriority(int value) {}

    @Override
    public int getPriority() {
      return 4;
    }

    @Override
    public void setTimeToLive(long value) {}

    @Override
    public long getTimeToLive() {
      return 0;
    }

    @Override
    public void setDeliveryDelay(long value) {}

    @Override
    public long getDeliveryDelay() {
      return 0;
    }
  }
}
