/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.impl.producer.TopicPublishInfo;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.ServiceState;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;

abstract class AbstractRocketMqSendHookTest {

  private static final ContextKey<String> CALLER_VALUE = ContextKey.named("send-hook-caller");

  abstract InstrumentationExtension testing();

  abstract MQProducer configureProducer(DefaultMQProducer producer);

  abstract boolean hasBatchCreateSpans();

  @SuppressWarnings("deprecation") // Hook registration requires the implementation accessor.
  @ParameterizedTest
  @MethodSource("sendModes")
  void scopesHooksBeforeAndAfterInstrumentation(
      boolean batch,
      CommunicationMode mode,
      boolean deferred,
      boolean failure,
      boolean callerSpan,
      boolean retry)
      throws Exception {
    InstrumentationExtension testing = testing();
    DefaultMQProducer producer = new DefaultMQProducer("hook-test");
    DefaultMQProducerImpl implementation =
        new DefaultMQProducerImpl(producer) {
          @Override
          public void start() {}
        };
    // Keep the real public send API, hook dispatch, encoding, and kernel; replace only startup and
    // the network transport so completion and failure boundaries can be exercised
    // deterministically.
    setField(producer, DefaultMQProducer.class, "defaultMQProducerImpl", implementation);
    setField(implementation, DefaultMQProducerImpl.class, "serviceState", ServiceState.RUNNING);
    MQClientInstance factory =
        mock(
            MQClientInstance.class,
            invocation -> {
              if (invocation.getMethod().getName().equals("getBrokerNameFromMessageQueue")) {
                return ((MessageQueue) invocation.getArgument(0)).getBrokerName();
              }
              return Answers.RETURNS_DEFAULTS.answer(invocation);
            });
    when(factory.getClientConfig()).thenReturn(producer);
    when(factory.findBrokerAddressInPublish("broker")).thenReturn("127.0.0.1:10911");
    setField(implementation, DefaultMQProducerImpl.class, "mQClientFactory", factory);
    if (retry) {
      producer.setRetryTimesWhenSendFailed(1);
      TopicPublishInfo route = new TopicPublishInfo();
      route.setHaveTopicRouterInfo(true);
      route.setMessageQueueList(singletonList(new MessageQueue("topic", "broker", 0)));
      implementation.getTopicPublishInfoTable().put("topic", route);
    }
    ExecutorService sender = Executors.newSingleThreadExecutor();
    producer.setAsyncSenderExecutor(sender);
    Span parent =
        callerSpan
            ? testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan()
            : Span.getInvalid();
    Context caller = Context.root().with(parent).with(CALLER_VALUE, "caller");
    AtomicReference<Context> early = new AtomicReference<>();
    AtomicReference<Context> late = new AtomicReference<>();
    AtomicReference<SpanContext> transportSpan = new AtomicReference<>();
    AtomicReference<SpanContext> preparationSpan = new AtomicReference<>();
    AtomicReference<String> propagation = new AtomicReference<>();
    AtomicInteger attempts = new AtomicInteger();
    AtomicReference<Runnable> completion = new AtomicReference<>();
    CompletableFuture<Void> prepared = new CompletableFuture<>();
    CompletableFuture<Void> callbackDone = new CompletableFuture<>();
    MQBrokerException error = new MQBrokerException(1, "send failed");
    AtomicReference<Throwable> expectedError = new AtomicReference<>(error);
    SendResult result = new SendResult(SendStatus.SEND_OK, "id", "offset", null, 0);
    MQClientAPIImpl transport =
        mock(
            MQClientAPIImpl.class,
            invocation -> {
              if (!invocation.getMethod().getName().equals("sendMessage")) {
                return Answers.RETURNS_DEFAULTS.answer(invocation);
              }
              transportSpan.set(Span.current().getSpanContext());
              Message message = invocation.getArgument(2);
              Message propagated =
                  batch
                      ? MessageDecoder.decodeMessages(ByteBuffer.wrap(message.getBody())).get(0)
                      : message;
              propagation.set(propagated.getProperty("traceparent"));
              if (attempts.incrementAndGet() == 1 && retry) {
                throw new RemotingConnectException("broker");
              }
              SendMessageContext request = null;
              SendCallback callback = null;
              for (Object argument : invocation.getArguments()) {
                if (argument instanceof SendMessageContext) {
                  request = (SendMessageContext) argument;
                } else if (argument instanceof SendCallback) {
                  callback = (SendCallback) argument;
                }
              }
              if (mode == CommunicationMode.ASYNC) {
                SendMessageContext capturedRequest = request;
                SendCallback capturedCallback = callback;
                Runnable complete =
                    () -> {
                      Context previous = Context.current();
                      capturedRequest.setSendResult(failure ? null : result);
                      capturedRequest.setException(failure ? error : null);
                      implementation.executeSendMessageHookAfter(capturedRequest);
                      if (failure) {
                        capturedCallback.onException(error);
                      } else {
                        capturedCallback.onSuccess(result);
                      }
                      assertCurrentContext(previous);
                    };
                if (deferred) {
                  completion.set(complete);
                } else {
                  complete.run();
                }
                prepared.complete(null);
                return null;
              }
              if (failure) {
                throw error;
              }
              return mode == CommunicationMode.ONEWAY ? null : result;
            });
    when(factory.getMQClientAPIImpl()).thenReturn(transport);
    implementation.registerSendMessageHook(hook("early", early, true));
    MQProducer sendingProducer = configureProducer(producer);
    sendingProducer.start();
    implementation.registerSendMessageHook(hook("late", late, false));
    SendCallback callback =
        new SendCallback() {
          @Override
          public void onSuccess(SendResult sendResult) {
            assertThat(failure).isFalse();
            assertThat(sendResult).isSameAs(result);
            complete();
          }

          @Override
          public void onException(Throwable e) {
            assertThat(failure).isTrue();
            assertThat(e).isSameAs(error);
            complete();
          }

          private void complete() {
            try {
              assertThat(Context.current().get(CALLER_VALUE)).isEqualTo("caller");
              assertThat(Span.current().getSpanContext()).isEqualTo(parent.getSpanContext());
              assertThat(testing.spans()).anyMatch(span -> span.getName().equals("send topic"));
              testing.runWithSpan("callback", () -> {});
              callbackDone.complete(null);
            } catch (Throwable t) {
              callbackDone.completeExceptionally(t);
              throw t;
            }
          }
        };
    try {
      try (Scope ignored = caller.makeCurrent()) {
        if (failure && mode != CommunicationMode.ASYNC) {
          Throwable thrown =
              catchThrowable(
                  () -> send(sendingProducer, batch, mode, callback, retry, preparationSpan));
          if (retry || mode == CommunicationMode.ONEWAY) {
            assertThat(thrown).hasCause(error);
          } else {
            assertThat(thrown).isSameAs(error);
          }
          if (retry) {
            expectedError.set(thrown);
          }
        } else {
          send(sendingProducer, batch, mode, callback, retry, preparationSpan);
        }
        assertCurrentContext(caller);
      }
      if (mode == CommunicationMode.ASYNC) {
        prepared.get(10, SECONDS);
        sender.submit(() -> {}).get(10, SECONDS);
        if (deferred) {
          CompletableFuture.runAsync(
                  () -> testing.runWithSpan("completion-thread", () -> completion.get().run()))
              .join();
        }
        callbackDone.get(10, SECONDS);
      }
      assertThat(transportSpan.get().isValid()).isTrue();
      assertThat(preparationSpan.get()).isEqualTo(transportSpan.get());
      assertThat(attempts.get()).isEqualTo(retry ? 2 : 1);
      for (Context hookContext : new Context[] {early.get(), late.get()}) {
        assertThat(hookContext.get(CALLER_VALUE)).isEqualTo("caller");
        assertThat(Span.fromContext(hookContext).getSpanContext()).isEqualTo(transportSpan.get());
      }
      assertThat(propagation.get()).isNotNull();
      if (!batch || !hasBatchCreateSpans()) {
        assertThat(propagation.get()).contains(transportSpan.get().getSpanId());
      }
    } finally {
      parent.end();
      producer.shutdown();
      sender.shutdownNow();
    }

    List<String> names = new ArrayList<>();
    if (batch && hasBatchCreateSpans()) {
      names.add("create topic");
    }
    names.add("send topic");
    if (!callerSpan && deferred) {
      names.add("completion-thread");
    }
    if (mode == CommunicationMode.ASYNC) {
      names.add("callback");
    }
    List<Consumer<TraceAssert>> traces = new ArrayList<>();
    if (callerSpan) {
      List<Consumer<SpanDataAssert>> spans = new ArrayList<>();
      spans.add(span -> span.hasName("parent").hasNoParent());
      for (String name : names) {
        spans.add(
            span -> {
              span.hasName(name)
                  .hasTraceId(parent.getSpanContext().getTraceId())
                  .hasParentSpanId(parent.getSpanContext().getSpanId());
              assertSendSpan(span, name, transportSpan.get(), failure, expectedError.get());
            });
      }
      traces.add(trace -> trace.hasSpansSatisfyingExactlyInAnyOrder(spans));
    } else {
      for (String name : names) {
        traces.add(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      span.hasName(name).hasNoParent();
                      assertSendSpan(span, name, transportSpan.get(), failure, expectedError.get());
                    }));
      }
    }
    if (callerSpan && deferred) {
      traces.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("completion-thread").hasNoParent()));
    }
    testing.waitAndAssertTraces(traces);
  }

  private static void assertSendSpan(
      SpanDataAssert span, String name, SpanContext expected, boolean failure, Throwable error) {
    if (!name.equals("send topic")) {
      return;
    }
    span.hasSpanId(expected.getSpanId())
        .hasStatus(failure ? StatusData.error() : StatusData.unset())
        .satisfies(
            data -> {
              assertThat(data.getAttributes().get(booleanKey("hook.early"))).isTrue();
              assertThat(data.getAttributes().get(booleanKey("hook.late"))).isTrue();
            });
    if (failure) {
      span.hasException(error);
    }
  }

  private static void assertCurrentContext(Context expected) {
    assertThat(Context.current().get(CALLER_VALUE)).isEqualTo(expected.get(CALLER_VALUE));
    assertThat(Span.current().getSpanContext())
        .isEqualTo(Span.fromContext(expected).getSpanContext());
  }

  private static SendMessageHook hook(
      String name, AtomicReference<Context> observed, boolean throwing) {
    return new SendMessageHook() {
      @Override
      public String hookName() {
        return name;
      }

      @Override
      public void sendMessageBefore(SendMessageContext context) {
        observed.set(Context.current());
        Span.current().setAttribute("hook." + name, true);
        if (throwing) {
          throw new IllegalStateException("user hook");
        }
      }

      @Override
      public void sendMessageAfter(SendMessageContext context) {}
    };
  }

  private static void send(
      MQProducer producer,
      boolean batch,
      CommunicationMode mode,
      SendCallback callback,
      boolean retry,
      AtomicReference<SpanContext> preparationSpan)
      throws InterruptedException, MQBrokerException, MQClientException, RemotingException {
    Message message =
        new Message() {
          @Override
          public byte[] getBody() {
            preparationSpan.set(Span.current().getSpanContext());
            return super.getBody();
          }
        };
    message.setTopic("topic");
    message.setBody("body".getBytes(UTF_8));
    assertThat(message.getProperties()).isNull();
    MessageQueue queue = new MessageQueue("topic", "broker", 0);
    if (retry) {
      producer.send(singletonList(message));
    } else if (batch) {
      if (mode == CommunicationMode.ASYNC) {
        producer.send(singletonList(message), queue, callback);
      } else {
        producer.send(singletonList(message), queue);
      }
    } else if (mode == CommunicationMode.ASYNC) {
      producer.send(message, queue, callback);
    } else if (mode == CommunicationMode.ONEWAY) {
      producer.sendOneway(message, queue);
    } else {
      producer.send(message, queue);
    }
  }

  private static void setField(Object target, Class<?> owner, String name, Object value)
      throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Stream<Arguments> sendModes() {
    List<Arguments> arguments = new ArrayList<>();
    for (boolean batch : new boolean[] {false, true}) {
      for (CommunicationMode mode : CommunicationMode.values()) {
        if (batch && mode == CommunicationMode.ONEWAY) {
          continue;
        }
        for (boolean deferred : new boolean[] {false, true}) {
          if (deferred && mode != CommunicationMode.ASYNC) {
            continue;
          }
          for (boolean failure : new boolean[] {false, true}) {
            for (boolean parent : new boolean[] {false, true}) {
              arguments.add(Arguments.of(batch, mode, deferred, failure, parent, false));
            }
          }
        }
      }
    }
    arguments.add(Arguments.of(true, CommunicationMode.SYNC, false, false, true, true));
    arguments.add(Arguments.of(true, CommunicationMode.SYNC, false, true, true, true));
    return arguments.stream();
  }
}
