/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

abstract class AbstractRocketMqBatchCallbackTest {

  private static final ContextKey<String> CALLER_VALUE = ContextKey.named("caller-value");

  abstract InstrumentationExtension testing();

  abstract MQProducer configureProducer(DefaultMQProducer producer);

  abstract boolean hasBatchCreateSpans();

  @ParameterizedTest
  @MethodSource("callbackModes")
  void restoresCallerContext(
      boolean asynchronous, boolean failure, boolean callerSpan, boolean throwingCallback)
      throws Exception {
    InstrumentationExtension testing = testing();
    Span parent =
        callerSpan
            ? testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan()
            : Span.getInvalid();
    Context caller = Context.root().with(parent).with(CALLER_VALUE, "caller");
    IllegalStateException sendError = new IllegalStateException("send failed");
    IllegalArgumentException callbackError = new IllegalArgumentException("callback failed");
    SendResult result = new SendResult(SendStatus.SEND_OK, "batch-id", "offset-id", null, 0);
    AtomicReference<Runnable> completion = new AtomicReference<>();
    DefaultMQProducer producer = new DefaultMQProducer("callback-test");
    // Replace only the transport boundary; keep the real batch API and encoding path.
    DefaultMQProducerImpl implementation =
        new DefaultMQProducerImpl(producer) {
          @Override
          public void send(Message message, SendCallback callback) {
            Runnable complete =
                () -> {
                  Context beforeCallback = Context.current();
                  try {
                    if (failure) {
                      callback.onException(sendError);
                    } else {
                      callback.onSuccess(result);
                    }
                  } finally {
                    assertCurrentContext(beforeCallback);
                  }
                };
            if (asynchronous) {
              completion.set(complete);
            } else {
              complete.run();
            }
          }
        };
    Field implementationField = DefaultMQProducer.class.getDeclaredField("defaultMQProducerImpl");
    implementationField.setAccessible(true);
    implementationField.set(producer, implementation);
    MQProducer sendingProducer = configureProducer(producer);
    SendCallback callback =
        new SendCallback() {
          @Override
          public void onSuccess(SendResult sendResult) {
            assertThat(failure).isFalse();
            assertThat(sendResult).isSameAs(result);
            onCompletion();
          }

          @Override
          public void onException(Throwable e) {
            assertThat(failure).isTrue();
            assertThat(e).isSameAs(sendError);
            onCompletion();
          }

          private void onCompletion() {
            assertThat(Context.current().get(CALLER_VALUE)).isEqualTo("caller");
            assertThat(Span.current().getSpanContext()).isEqualTo(parent.getSpanContext());
            assertThat(testing.spans()).anyMatch(span -> span.getName().equals("send topic"));
            testing.runWithSpan("callback", () -> {});
            if (throwingCallback) {
              throw callbackError;
            }
          }
        };
    try {
      try (Scope ignored = caller.makeCurrent()) {
        if (!asynchronous && throwingCallback) {
          assertThatThrownBy(
                  () ->
                      sendingProducer.send(
                          singletonList(new Message("topic", "body".getBytes(UTF_8))), callback))
              .isSameAs(callbackError);
        } else {
          sendingProducer.send(
              singletonList(new Message("topic", "body".getBytes(UTF_8))), callback);
        }
        assertCurrentContext(caller);
      }
      if (asynchronous) {
        CompletableFuture.runAsync(
                () ->
                    testing.runWithSpan(
                        "completion-thread",
                        () -> {
                          Context previous = Context.current().with(CALLER_VALUE, "completion");
                          try (Scope ignored = previous.makeCurrent()) {
                            if (throwingCallback) {
                              assertThatThrownBy(() -> completion.get().run())
                                  .isSameAs(callbackError);
                            } else {
                              completion.get().run();
                            }
                            assertCurrentContext(previous);
                          }
                        }))
            .join();
      }
    } finally {
      parent.end();
    }

    List<String> operationSpans = new ArrayList<>();
    if (hasBatchCreateSpans()) {
      operationSpans.add("create topic");
    }
    operationSpans.add("send topic");
    if (!callerSpan && asynchronous) {
      operationSpans.add("completion-thread");
    }
    operationSpans.add("callback");
    List<Consumer<TraceAssert>> traces = new ArrayList<>();
    if (callerSpan) {
      List<Consumer<SpanDataAssert>> spans = new ArrayList<>();
      spans.add(span -> span.hasName("parent").hasNoParent());
      for (String name : operationSpans) {
        spans.add(
            span ->
                span.hasName(name)
                    .hasTraceId(parent.getSpanContext().getTraceId())
                    .hasParentSpanId(parent.getSpanContext().getSpanId()));
      }
      traces.add(trace -> trace.hasSpansSatisfyingExactlyInAnyOrder(spans));
    } else {
      for (String name : operationSpans) {
        traces.add(
            trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName(name).hasNoParent()));
      }
    }
    if (callerSpan && asynchronous) {
      traces.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("completion-thread").hasNoParent()));
    }
    testing.waitAndAssertTraces(traces);
  }

  private static void assertCurrentContext(Context expected) {
    assertThat(Context.current().get(CALLER_VALUE)).isEqualTo(expected.get(CALLER_VALUE));
    assertThat(Span.current().getSpanContext())
        .isEqualTo(Span.fromContext(expected).getSpanContext());
  }

  private static Stream<Arguments> callbackModes() {
    return Stream.of(false, true)
        .flatMap(
            asynchronous ->
                Stream.of(false, true)
                    .flatMap(
                        failure ->
                            Stream.of(false, true)
                                .flatMap(
                                    callerSpan ->
                                        Stream.of(false, true)
                                            .map(
                                                throwing ->
                                                    Arguments.of(
                                                        asynchronous,
                                                        failure,
                                                        callerSpan,
                                                        throwing)))));
  }
}
