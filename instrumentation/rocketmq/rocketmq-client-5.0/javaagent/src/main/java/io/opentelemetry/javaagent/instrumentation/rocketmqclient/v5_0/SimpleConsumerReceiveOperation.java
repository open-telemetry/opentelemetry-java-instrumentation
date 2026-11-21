/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0.RocketMqSingletons.simpleConsumerReceiveInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.ExceptionLogger;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.AbstractFuture;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.Futures;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.ListenableFuture;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.MoreExecutors;

public class SimpleConsumerReceiveOperation {

  private final String consumerGroup;
  private final Context parentContext;
  private final Timer timer;

  /**
   * Returns whether the receive that {@code consumer} is about to perform is recorded here instead
   * of by the per-message-queue instrumentation underneath it.
   */
  public static boolean handlesReceive(Object consumer) {
    return consumer instanceof SimpleConsumer;
  }

  public static SimpleConsumerReceiveOperation start(SimpleConsumer consumer) {
    return new SimpleConsumerReceiveOperation(
        consumer.getConsumerGroup(), Context.current(), Timer.start());
  }

  private SimpleConsumerReceiveOperation(String consumerGroup, Context parentContext, Timer timer) {
    this.consumerGroup = consumerGroup;
    this.parentContext = parentContext;
    this.timer = timer;
  }

  public ListenableFuture<List<MessageView>> wrap(
      ListenableFuture<List<MessageView>> originalFuture) {
    return new ReceiveFuture(originalFuture, this);
  }

  private void endSuccessfully(List<MessageView> messages) {
    try {
      end(messages, null);
    } catch (Throwable t) {
      ExceptionLogger.logSuppressedError("Error instrumenting RocketMQ SimpleConsumer receive", t);
    }
  }

  private void endSafely(@Nullable List<MessageView> messages, Throwable error) {
    try {
      end(messages, error);
    } catch (Throwable t) {
      ExceptionLogger.logSuppressedError(
          "Error instrumenting RocketMQ SimpleConsumer receive failure", t);
    }
  }

  private void end(@Nullable List<MessageView> messages, @Nullable Throwable error) {
    if (error == null && messages != null && messages.isEmpty()) {
      return;
    }
    RocketMqReceiveRequest request =
        messages == null
            ? RocketMqReceiveRequest.create(consumerGroup)
            : RocketMqReceiveRequest.create(consumerGroup, messages);
    Instrumenter<RocketMqReceiveRequest, List<MessageView>> instrumenter =
        simpleConsumerReceiveInstrumenter();
    if (instrumenter.shouldStart(parentContext, request)) {
      InstrumenterUtil.startAndEnd(
          instrumenter, parentContext, request, messages, error, timer.startTime(), timer.now());
    }
  }

  private static final class ReceiveFuture extends AbstractFuture<List<MessageView>> {

    @Nullable private ListenableFuture<List<MessageView>> originalFuture;

    private ReceiveFuture(
        ListenableFuture<List<MessageView>> sourceFuture,
        SimpleConsumerReceiveOperation operation) {
      this.originalFuture = sourceFuture;

      // Observe source completion even if this future is cancelled. Publish its outcome only
      // after telemetry; setFuture preserves success, failure, and cancellation.
      Futures.addCallback(
          sourceFuture,
          new FutureCallback<List<MessageView>>() {
            @Override
            public void onSuccess(List<MessageView> messages) {
              operation.endSuccessfully(messages);
              setFuture(sourceFuture);
            }

            @Override
            public void onFailure(Throwable t) {
              operation.endSafely(null, t);
              setFuture(sourceFuture);
            }
          },
          MoreExecutors.directExecutor());
    }

    @Override
    @SuppressWarnings("Interruption") // propagate the caller's cancellation interrupt flag
    protected void afterDone() {
      ListenableFuture<List<MessageView>> future = originalFuture;
      // Release this reference; the callback captures sourceFuture independently.
      originalFuture = null;
      if (isCancelled() && future != null) {
        future.cancel(wasInterrupted());
      }
    }
  }
}
