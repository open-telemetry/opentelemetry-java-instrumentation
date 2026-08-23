/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0.RocketMqSingletons.consumerReceiveInstrumenter;
import static java.util.Collections.emptyList;

import apache.rocketmq.v2.ReceiveMessageRequest;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import java.util.List;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.impl.consumer.ReceiveMessageResult;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;

public class ReceiveSpanFinishingCallback implements FutureCallback<ReceiveMessageResult> {

  private final ReceiveMessageRequest request;
  private final Timer timer;

  public ReceiveSpanFinishingCallback(ReceiveMessageRequest request, Timer timer) {
    this.request = request;
    this.timer = timer;
  }

  @Override
  public void onSuccess(ReceiveMessageResult receiveMessageResult) {
    List<MessageView> messageViews = receiveMessageResult.getMessageViews();
    // Don't create spans when no messages were received.
    if (messageViews.isEmpty()) {
      return;
    }
    String consumerGroup = request.getGroup().getName();
    for (MessageView messageView : messageViews) {
      VirtualFieldStore.setConsumerGroupByMessage(messageView, consumerGroup);
    }
    Instrumenter<RocketMqReceiveRequest, List<MessageView>> receiveInstrumenter =
        consumerReceiveInstrumenter();
    Context parentContext = Context.current();
    if (emitStableMessagingSemconv()) {
      // the process span parents to the context that the messages were received in, and links to
      // the producer context that the message headers point at; the receive span links to that
      // same producer context, and its own context is deliberately not handed to the process span
      for (MessageView messageView : messageViews) {
        VirtualFieldStore.setContextByMessage(messageView, parentContext);
      }
    }
    RocketMqReceiveRequest receiveRequest = RocketMqReceiveRequest.create(request, messageViews);
    if (receiveInstrumenter.shouldStart(parentContext, receiveRequest)) {
      Context context =
          InstrumenterUtil.startAndEnd(
              receiveInstrumenter,
              parentContext,
              receiveRequest,
              messageViews,
              null,
              timer.startTime(),
              timer.now());
      if (!emitStableMessagingSemconv()) {
        for (MessageView messageView : messageViews) {
          VirtualFieldStore.setContextByMessage(messageView, context);
        }
      }
    }
  }

  @Override
  public void onFailure(Throwable throwable) {
    Instrumenter<RocketMqReceiveRequest, List<MessageView>> receiveInstrumenter =
        consumerReceiveInstrumenter();
    Context parentContext = Context.current();
    RocketMqReceiveRequest receiveRequest = RocketMqReceiveRequest.create(request, emptyList());
    if (receiveInstrumenter.shouldStart(parentContext, receiveRequest)) {
      InstrumenterUtil.startAndEnd(
          receiveInstrumenter,
          parentContext,
          receiveRequest,
          null,
          throwable,
          timer.startTime(),
          timer.now());
    }
  }
}
