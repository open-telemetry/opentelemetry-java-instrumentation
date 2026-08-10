/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0.RocketMqSingletons.simpleConsumerReceiveInstrumenter;
import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.ExceptionLogger;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.Futures;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.ListenableFuture;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.MoreExecutors;

public class SimpleConsumerReceiveOperation {

  private final String consumerGroup;
  private final Context parentContext;
  private final Timer timer;

  public static boolean shouldInstrument(Object consumer) {
    return emitStableMessagingSemconv() && consumer instanceof SimpleConsumer;
  }

  @Nullable
  public static SimpleConsumerReceiveOperation start(SimpleConsumer consumer) {
    if (!emitStableMessagingSemconv()) {
      return null;
    }
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
    Futures.addCallback(
        originalFuture,
        new FutureCallback<List<MessageView>>() {
          @Override
          public void onSuccess(List<MessageView> unused) {}

          @Override
          public void onFailure(Throwable t) {
            endSafely(null, t);
          }
        },
        MoreExecutors.directExecutor());
    return Futures.transform(originalFuture, this::endSuccessfully, MoreExecutors.directExecutor());
  }

  private List<MessageView> endSuccessfully(List<MessageView> messages) {
    try {
      if (messages.isEmpty()) {
        return messages;
      }
      for (MessageView message : messages) {
        VirtualFieldStore.setConsumerGroupByMessage(message, consumerGroup);
        VirtualFieldStore.setContextByMessage(message, parentContext);
      }
      end(messages, null);
      return SimpleConsumerTracingList.wrap(messages);
    } catch (Throwable t) {
      ExceptionLogger.logSuppressedError("Error instrumenting RocketMQ SimpleConsumer receive", t);
      return messages;
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
    List<MessageView> requestMessages = messages == null ? emptyList() : messages;
    RocketMqReceiveRequest request = RocketMqReceiveRequest.create(consumerGroup, requestMessages);
    Instrumenter<RocketMqReceiveRequest, List<MessageView>> instrumenter =
        simpleConsumerReceiveInstrumenter();
    if (instrumenter.shouldStart(parentContext, request)) {
      InstrumenterUtil.startAndEnd(
          instrumenter, parentContext, request, messages, error, timer.startTime(), timer.now());
    }
  }
}
