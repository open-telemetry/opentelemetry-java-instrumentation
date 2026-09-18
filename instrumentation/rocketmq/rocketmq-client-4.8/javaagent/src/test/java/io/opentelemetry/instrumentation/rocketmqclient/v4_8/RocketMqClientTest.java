/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.base.BaseConf;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageBatch;
import org.apache.rocketmq.remoting.exception.RemotingTooMuchRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RocketMqClientTest extends AbstractRocketMqClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  InstrumentationExtension testing() {
    return testing;
  }

  @Override
  void configureMqProducer(DefaultMQProducer producer) {}

  @Override
  void configureMqPushConsumer(DefaultMQPushConsumer consumer) {}

  @Override
  boolean hasBatchCreateSpans() {
    return !Boolean.getBoolean("testBatchCreateSpansDisabled");
  }

  @Override
  boolean isJavaagent() {
    return true;
  }

  @Test
  void testNestedBatchSendRestoresOuterState() throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    String topic = BaseConf.initTopic();
    List<Message> inner =
        asList(
            new Message(topic, "inner one".getBytes(UTF_8)),
            new Message(topic, "inner two".getBytes(UTF_8)));
    List<Message> outer =
        asList(
            new Message(topic, "outer one".getBytes(UTF_8)),
            new Message(topic, "outer two".getBytes(UTF_8)));
    AtomicBoolean nestedSent = new AtomicBoolean();
    Collection<Message> nestingBatch =
        new AbstractCollection<Message>() {
          @Override
          public Iterator<Message> iterator() {
            if (nestedSent.compareAndSet(false, true)) {
              try {
                producer().send(inner);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            }
            return outer.iterator();
          }

          @Override
          public int size() {
            return outer.size();
          }
        };

    assertThat(producer().send(nestingBatch).getSendStatus()).isEqualTo(SendStatus.SEND_OK);
    assertThat(nestedSent).isTrue();
    assertThat(inner)
        .allSatisfy(message -> assertThat(message.getProperty("traceparent")).isNotNull());
    assertThat(outer)
        .allSatisfy(message -> assertThat(message.getProperty("traceparent")).isNotNull());
    testing().waitForTraces(hasBatchCreateSpans() ? 6 : 2);
  }

  @Test
  void testBatchSendStateCleanupOnException() {
    assumeTrue(emitStableMessagingSemconv());
    List<Message> messages =
        asList(
            new Message("unused", "one".getBytes(UTF_8)),
            new Message("unused", "two".getBytes(UTF_8)));

    assertThatThrownBy(() -> producer().send(failingBatch())).isInstanceOf(MQClientException.class);

    MessageBatch.generateFromList(messages).encode();
    assertThat(messages)
        .allSatisfy(message -> assertThat(message.getProperty("traceparent")).isNull());
  }

  @Test
  void testBatchSendSuppression() throws Exception {
    assumeTrue(Boolean.getBoolean("testBatchSendSuppression"));
    String topic = BaseConf.initTopic();
    Instrumenter<String, Void> parentInstrumenter =
        Instrumenter.<String, Void>builder(
                testing().getOpenTelemetry(), "test-parent", request -> request)
            .buildInstrumenter(request -> CLIENT);
    Context parentContext = parentInstrumenter.start(Context.root(), "parent");
    try (Scope ignored = parentContext.makeCurrent()) {
      producer()
          .send(
              asList(
                  new Message(topic, "one".getBytes(UTF_8)),
                  new Message(topic, "two".getBytes(UTF_8))));
    } finally {
      parentInstrumenter.end(parentContext, "parent", null, null);
    }

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactlyInAnyOrder(
                    span -> span.hasName("parent").hasKind(CLIENT),
                    span -> span.hasName("create " + topic).hasKind(PRODUCER),
                    span -> span.hasName("create " + topic).hasKind(PRODUCER)));
  }

  @SuppressWarnings("deprecation")
  @Test
  void testAsyncBatchQueueTimeout() throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    String topic = BaseConf.initTopic();
    CapturingExecutor executor = new CapturingExecutor();
    ExecutorService originalExecutor =
        producer().getDefaultMQProducerImpl().getAsyncSenderExecutor();
    producer().setAsyncSenderExecutor(executor);
    CompletableFuture<Throwable> callbackError = new CompletableFuture<>();
    try {
      testing()
          .runWithSpan(
              "parent",
              () ->
                  producer()
                      .send(
                          asList(
                              new Message(topic, "one".getBytes(UTF_8)),
                              new Message(topic, "two".getBytes(UTF_8))),
                          new SendCallback() {
                            @Override
                            public void onSuccess(SendResult sendResult) {
                              callbackError.complete(null);
                            }

                            @Override
                            public void onException(Throwable e) {
                              callbackError.complete(e);
                            }
                          },
                          0));

      assertThat(testing().spans()).noneMatch(span -> ("send " + topic).equals(span.getName()));
      executor.runTask();
      assertThat(callbackError.get(10, SECONDS))
          .isInstanceOf(RemotingTooMuchRequestException.class);
    } finally {
      producer().setAsyncSenderExecutor(originalExecutor);
    }

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(span -> span.hasName("parent"));
              if (hasBatchCreateSpans()) {
                assertions.add(span -> span.hasName("create " + topic).hasKind(PRODUCER));
                assertions.add(span -> span.hasName("create " + topic).hasKind(PRODUCER));
              }
              assertions.add(
                  span ->
                      span.hasName("send " + topic)
                          .hasKind(hasBatchCreateSpans() ? CLIENT : PRODUCER)
                          .hasStatus(StatusData.error()));
              trace.hasSpansSatisfyingExactlyInAnyOrder(assertions);
            });
  }

  private static Collection<Message> failingBatch() {
    return new AbstractCollection<Message>() {
      @Override
      public Iterator<Message> iterator() {
        throw new IllegalStateException("batch iteration failed");
      }

      @Override
      public int size() {
        return 1;
      }
    };
  }

  private static final class CapturingExecutor extends AbstractExecutorService {
    private Runnable task;
    private boolean shutdown;

    private void runTask() {
      assertThat(task).isNotNull();
      task.run();
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return emptyList();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return shutdown;
    }

    @Override
    public void execute(Runnable command) {
      task = command;
    }
  }
}
