/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageBatch;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;

@SuppressWarnings("deprecation") // asserting incubating messaging attributes
class RocketMqProducerWrapperTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final AtomicReference<SendMessageHook> hook = new AtomicReference<>();
  private final AtomicReference<Collection<Message>> sentMessages = new AtomicReference<>();
  private final DefaultMQProducerImpl implementation = mock(DefaultMQProducerImpl.class);

  @BeforeEach
  void setUp() {
    doAnswer(
            invocation -> {
              hook.set(invocation.getArgument(0));
              return null;
            })
        .when(implementation)
        .registerSendMessageHook(any());
  }

  @ParameterizedTest
  @MethodSource("batchSendMethods")
  void propagatesBeforeEncodingForEveryBatchOverload(Method method, int messageCount)
      throws Exception {
    List<Message> messages = messages(messageCount);
    AtomicReference<byte[]> encoded = new AtomicReference<>();
    AtomicReference<SendResult> callbackResult = new AtomicReference<>();
    SendResult result = new SendResult(SendStatus.SEND_OK, "batch-id", "offset-id", null, 0);
    MQProducer producer =
        wrap(
            invocation -> {
              if (invocation.getMethod().getName().equals("send")) {
                Collection<Message> sent = invocation.getArgument(0);
                SendMessageContext request = encodedRequest(sent);
                request.setCommunicationMode(
                    invocation.getMethod().getReturnType() == void.class
                        ? CommunicationMode.ASYNC
                        : CommunicationMode.SYNC);
                encoded.set(request.getMessage().getBody());
                hook.get().sendMessageBefore(request);
                assertThat(request.getMessage().getBody()).isSameAs(encoded.get());
                request.setSendResult(result);
                hook.get().sendMessageAfter(request);
                for (Object arg : invocation.getArguments()) {
                  if (arg instanceof SendCallback) {
                    ((SendCallback) arg).onSuccess(result);
                  }
                }
                return invocation.getMethod().getReturnType() == void.class ? null : result;
              }
              return null;
            });
    Object[] args = new Object[method.getParameterCount()];
    args[0] = messages;
    for (int i = 1; i < args.length; i++) {
      Class<?> type = method.getParameterTypes()[i];
      if (type == long.class) {
        args[i] = 1_000L;
      } else if (type == MessageQueue.class) {
        args[i] = new MessageQueue("topic", "broker", 0);
      } else if (type == SendCallback.class) {
        args[i] =
            new SendCallback() {
              @Override
              public void onSuccess(SendResult sendResult) {
                callbackResult.set(sendResult);
              }

              @Override
              public void onException(Throwable e) {
                throw new AssertionError(e);
              }
            };
      } else {
        throw new AssertionError(type);
      }
    }
    testing.runWithSpan("parent", () -> method.invoke(producer, args));

    List<Message> decoded = MessageDecoder.decodeMessages(ByteBuffer.wrap(encoded.get()));
    assertThat(decoded).hasSize(messageCount);
    for (int i = 0; i < messageCount; i++) {
      Message message = decoded.get(i);
      assertThat(message.getProperty("traceparent"))
          .isNotNull()
          .isEqualTo(messages.get(i).getProperty("traceparent"));
      assertThat(message.getProperty("custom")).isEqualTo("value-" + i);
      assertThat(message.getBody()).isEqualTo(messages.get(i).getBody());
      assertThat(message.getFlag()).isEqualTo(i);
    }
    assertStateCleared();
    if (method.getReturnType() == void.class) {
      assertThat(callbackResult.get()).isSameAs(result);
    }
    assertBatchTrace(messageCount);
  }

  private static Stream<Arguments> batchSendMethods() {
    return Stream.of(MQProducer.class.getMethods())
        .filter(
            method ->
                method.getName().equals("send")
                    && method.getParameterCount() > 0
                    && method.getParameterTypes()[0] == Collection.class)
        .flatMap(method -> IntStream.of(1, 2).mapToObj(count -> Arguments.of(method, count)));
  }

  @Test
  void retainsParentAndOperationUntilAsyncCompletion() throws Exception {
    List<Message> messages = messages(2);
    AtomicReference<Runnable> pendingSend = new AtomicReference<>();
    SendResult result = new SendResult(SendStatus.SEND_OK, "batch-id", "offset-id", null, 0);
    AtomicReference<SendResult> callbackResult = new AtomicReference<>();
    MQProducer producer =
        wrap(
            invocation -> {
              if (invocation.getMethod().getName().equals("send")) {
                Collection<Message> sent = invocation.getArgument(0);
                SendMessageContext request = encodedRequest(sent);
                request.setCommunicationMode(CommunicationMode.ASYNC);
                SendCallback callback = invocation.getArgument(1);
                pendingSend.set(
                    () -> {
                      hook.get().sendMessageBefore(request);
                      request.setSendResult(result);
                      hook.get().sendMessageAfter(request);
                      callback.onSuccess(result);
                    });
              }
              return null;
            });
    testing.runWithSpan(
        "parent",
        () ->
            producer.send(
                messages,
                new SendCallback() {
                  @Override
                  public void onSuccess(SendResult sendResult) {
                    callbackResult.set(sendResult);
                  }

                  @Override
                  public void onException(Throwable e) {
                    throw new AssertionError(e);
                  }
                }));
    assertThat(testing.spans()).noneMatch(span -> span.getName().equals("send topic"));
    CompletableFuture.runAsync(pendingSend.get()).join();
    assertThat(callbackResult.get()).isSameAs(result);
    assertBatchTrace(2);
  }

  @Test
  void completesOnAsyncFailureBeforeAnyHook() throws Exception {
    AtomicReference<SendCallback> callback = new AtomicReference<>();
    AtomicReference<Throwable> callbackError = new AtomicReference<>();
    MQProducer producer =
        wrap(
            invocation -> {
              if (invocation.getMethod().getName().equals("send")) {
                callback.set(invocation.getArgument(1));
              }
              return null;
            });
    List<Message> messages = messages(2);
    producer.send(
        messages,
        new SendCallback() {
          @Override
          public void onSuccess(SendResult sendResult) {
            throw new AssertionError(sendResult);
          }

          @Override
          public void onException(Throwable e) {
            callbackError.set(e);
          }
        });
    MQClientException error = new MQClientException("queue timeout", null);
    callback.get().onException(error);
    assertThat(callbackError.get()).isSameAs(error);
    assertStateCleared();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send topic").hasKind(PRODUCER).hasException(error)));
  }

  @Test
  void completesOnSynchronousFailureAndPreservesException() {
    MQClientException error = new MQClientException("failed", null);
    MQProducer producer =
        wrap(
            invocation -> {
              if (invocation.getMethod().getName().equals("send")) {
                throw error;
              }
              return null;
            });
    List<Message> messages = messages(2);
    assertThatThrownBy(() -> producer.send(messages)).isSameAs(error);
    assertStateCleared();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send topic").hasKind(PRODUCER).hasException(error)));
  }

  @Test
  void supportsNullAsyncCallback() throws Exception {
    MQProducer producer =
        wrap(
            invocation -> {
              if (invocation.getMethod().getName().equals("send")) {
                SendCallback callback = invocation.getArgument(1);
                callback.onSuccess(
                    new SendResult(SendStatus.SEND_OK, "batch-id", "offset-id", null, 0));
              }
              return null;
            });
    List<Message> messages = messages(2);
    producer.send(messages, (SendCallback) null);
    assertStateCleared();
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("send topic")));
  }

  @Test
  void doesNotStartAnotherOperationWhenSuppressed() throws Exception {
    MQProducer producer =
        wrap(
            invocation -> {
              if (invocation.getMethod().getName().equals("send")) {
                SendMessageContext request = encodedRequest(invocation.getArgument(0));
                hook.get().sendMessageBefore(request);
                hook.get().sendMessageAfter(request);
                return new SendResult(SendStatus.SEND_OK, "batch-id", "offset-id", null, 0);
              }
              return null;
            });
    Instrumenter<SendMessageContext, Void> outer =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            testing.getOpenTelemetry(), IncludeExclude.builder().build(), false);
    SendMessageContext request = new SendMessageContext();
    request.setMessage(new Message("outer", new byte[0]));
    Context context = outer.start(Context.root(), request);
    List<Message> messages = messages(2);
    try (Scope ignored = context.makeCurrent()) {
      producer.send(messages);
    } finally {
      outer.end(context, request, null, null);
    }
    assertStateCleared();
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("send outer")));
  }

  @Test
  void delegatesLifecycleAndSingleMessageSends() throws Exception {
    DefaultMQProducer producer = mock(DefaultMQProducer.class);
    when(producer.getDefaultMQProducerImpl()).thenReturn(implementation);
    MQProducer wrapped = RocketMqTelemetry.create(testing.getOpenTelemetry()).wrap(producer);
    Message message = messages(1).get(0);
    wrapped.start();
    wrapped.send(message);
    wrapped.sendOneway(message);
    wrapped.shutdown();
    verify(producer).start();
    verify(producer).send(message);
    verify(producer).sendOneway(message);
    verify(producer).shutdown();
  }

  @Test
  void deprecatedHookStillInstrumentsSingleMessages() {
    SendMessageHook hook =
        RocketMqTelemetry.create(testing.getOpenTelemetry()).createSendMessageHook();
    SendMessageContext request = new SendMessageContext();
    request.setMessage(messages(1).get(0));
    request.setCommunicationMode(CommunicationMode.ONEWAY);
    hook.sendMessageBefore(request);
    hook.sendMessageAfter(request);
    assertThat(request.getMessage().getProperty("traceparent")).isNotNull();
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("send topic")));
  }

  @Test
  void isolatesOverlappingAsyncSendsThatReuseMessages() throws Exception {
    List<SendMessageContext> requests = new ArrayList<>();
    List<SendCallback> callbacks = new ArrayList<>();
    MQProducer producer =
        wrap(
            invocation -> {
              if (invocation.getMethod().getName().equals("send")) {
                requests.add(encodedRequest(invocation.getArgument(0)));
                callbacks.add(invocation.getArgument(1));
              }
              return null;
            });
    List<Message> messages = messages(2);
    testing.runWithSpan("first", () -> producer.send(messages, (SendCallback) null));
    testing.runWithSpan("second", () -> producer.send(messages, (SendCallback) null));
    SendResult result = new SendResult(SendStatus.SEND_OK, "batch-id", "offset-id", null, 0);
    hook.get().sendMessageBefore(requests.get(1));
    requests.get(1).setSendResult(result);
    hook.get().sendMessageAfter(requests.get(1));
    callbacks.get(1).onSuccess(result);
    hook.get().sendMessageBefore(requests.get(0));
    requests.get(0).setSendResult(result);
    hook.get().sendMessageAfter(requests.get(0));
    callbacks.get(0).onSuccess(result);
    for (SendMessageContext request : requests) {
      assertThat(RocketMqProducerWrapper.getState(request)).isNull();
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("first"),
                span -> span.hasName("send topic").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second"),
                span -> span.hasName("send topic").hasParent(trace.getSpan(0))));
  }

  @Test
  void sharesPayloadAndPreservesClientMessageMutations() throws Exception {
    List<Message> originals = messages(2);
    MQProducer producer =
        wrap(
            invocation -> {
              if (invocation.getMethod().getName().equals("send")) {
                List<Message> sent = invocation.getArgument(0);
                for (int i = 0; i < sent.size(); i++) {
                  assertThat(sent.get(i)).isNotSameAs(originals.get(i));
                  assertThat(sent.get(i).getBody()).isSameAs(originals.get(i).getBody());
                  sent.get(i).setTopic("namespace%topic");
                  assertThat(originals.get(i).getTopic()).isEqualTo("namespace%topic");
                  MessageAccessor.putProperty(sent.get(i), "UNIQ_KEY", "id-" + i);
                }
                return new SendResult(SendStatus.SEND_OK, "batch-id", "offset-id", null, 0);
              }
              return null;
            });
    producer.send(originals);
    for (int i = 0; i < originals.size(); i++) {
      assertThat(originals.get(i).getTopic()).isEqualTo("namespace%topic");
      assertThat(originals.get(i).getProperty("UNIQ_KEY")).isEqualTo("id-" + i);
    }
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("send topic")));
  }

  @Test
  void supportsMessagesWithoutAnInitializedPropertyMap() throws Exception {
    Message message = new Message();
    message.setTopic("topic");
    message.setBody("body".getBytes(UTF_8));
    MQProducer producer =
        wrap(
            invocation ->
                invocation.getMethod().getName().equals("send")
                    ? new SendResult(SendStatus.SEND_OK, "batch-id", "offset-id", null, 0)
                    : null);
    producer.send(singletonList(message));
    assertThat(message.getProperty("traceparent")).isNotNull();
    assertStateCleared();
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("send topic")));
  }

  private MQProducer wrap(Answer<Object> answer) {
    DefaultMQProducer producer =
        mock(
            DefaultMQProducer.class,
            invocation -> {
              if (invocation.getMethod().getName().equals("getDefaultMQProducerImpl")) {
                return implementation;
              }
              if (invocation.getMethod().getName().equals("send")) {
                sentMessages.set(invocation.getArgument(0));
              }
              return answer.answer(invocation);
            });
    return RocketMqTelemetry.create(testing.getOpenTelemetry()).wrap(producer);
  }

  private static List<Message> messages(int count) {
    List<Message> messages = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Message message = new Message("topic", ("body-" + i).getBytes(UTF_8));
      message.setFlag(i);
      message.putUserProperty("custom", "value-" + i);
      messages.add(message);
    }
    return messages;
  }

  private static SendMessageContext encodedRequest(Collection<Message> messages) {
    MessageBatch batch = MessageBatch.generateFromList(messages);
    batch.setBody(batch.encode());
    SendMessageContext request = new SendMessageContext();
    request.setMessage(batch);
    request.setCommunicationMode(CommunicationMode.SYNC);
    request.setBrokerAddr("localhost:10911");
    return request;
  }

  private void assertStateCleared() {
    SendMessageContext request = new SendMessageContext();
    request.setMessage(MessageBatch.generateFromList(sentMessages.get()));
    assertThat(RocketMqProducerWrapper.getState(request)).isNull();
  }

  private static void assertBatchTrace(int count) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("send topic")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "rocketmq"),
                            equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                            equalTo(MESSAGING_ROCKETMQ_NAMESPACE, ""),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, (long) count),
                            equalTo(MESSAGING_MESSAGE_ID, null))));
  }
}
