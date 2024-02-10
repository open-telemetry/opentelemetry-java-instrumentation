/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import apache.rocketmq.v2.ReceiveMessageRequest;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import java.util.List;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.impl.consumer.ReceiveMessageResult;
import org.apache.rocketmq.client.java.message.MessageViewImpl;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;

public final class ReceiveSpanFinishingCallback implements FutureCallback<ReceiveMessageResult> {

  private final ReceiveMessageRequest request;
  private final Timer timer;

  public ReceiveSpanFinishingCallback(ReceiveMessageRequest request, Timer timer) {
    this.request = request;
    this.timer = timer;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void onSuccess(ReceiveMessageResult receiveMessageResult) {
    List<MessageViewImpl> messageViews = receiveMessageResult.getMessageViewImpls();
    // Don't create spans when no messages were received.
    if (messageViews.isEmpty()) {
      return;
    }
    String consumerGroup = request.getGroup().getName();
    for (MessageViewImpl messageView : messageViews) {
      VirtualFieldStore.setConsumerGroupByMessage(messageView, consumerGroup);
    }
    Instrumenter<ReceiveMessageRequest, List<MessageView>> receiveInstrumenter =
        RocketMqSingletons.consumerReceiveInstrumenter();
    Context parentContext = Context.current();
    if (receiveInstrumenter.shouldStart(parentContext, request)) {
      Context context =
          InstrumenterUtil.startAndEnd(
              receiveInstrumenter,
              parentContext,
              request,
              (List<MessageView>) (List) messageViews,
              null,
              timer.startTime(),
              timer.now());
      for (MessageViewImpl messageView : messageViews) {
        VirtualFieldStore.setContextByMessage(messageView, context);
      }
    }
  }

  @Override
  public void onFailure(Throwable throwable) {
    Instrumenter<ReceiveMessageRequest, List<MessageView>> receiveInstrumenter =
        RocketMqSingletons.consumerReceiveInstrumenter();
    Context parentContext = Context.current();
    if (receiveInstrumenter.shouldStart(parentContext, request)) {
      InstrumenterUtil.startAndEnd(
          receiveInstrumenter,
          parentContext,
          request,
          null,
          throwable,
          timer.startTime(),
          timer.now());
    }
  }
}
