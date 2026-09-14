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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.base.BaseConf;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
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
            trace ->
                trace.hasSpansSatisfyingExactlyInAnyOrder(
                    span -> span.hasName("parent"),
                    span -> span.hasName("create " + topic).hasKind(PRODUCER),
                    span -> span.hasName("create " + topic).hasKind(PRODUCER),
                    span ->
                        span.hasName("send " + topic)
                            .hasKind(CLIENT)
                            .hasStatus(StatusData.error())));
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
