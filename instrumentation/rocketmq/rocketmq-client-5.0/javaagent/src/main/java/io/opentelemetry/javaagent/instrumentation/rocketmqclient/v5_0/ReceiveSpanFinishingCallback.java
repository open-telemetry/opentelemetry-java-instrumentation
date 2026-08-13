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
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingReceiveTelemetry;
import io.opentelemetry.instrumentation.api.internal.Timer;
import java.util.List;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.impl.consumer.ReceiveMessageResult;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;

public class ReceiveSpanFinishingCallback implements FutureCallback<ReceiveMessageResult> {

  private final ReceiveMessageRequest request;
  private final Timer timer;
  private final boolean pullApi;

  public ReceiveSpanFinishingCallback(ReceiveMessageRequest request, Timer timer, boolean pullApi) {
    this.request = request;
    this.timer = timer;
    this.pullApi = pullApi;
  }

  @Override
  public void onSuccess(ReceiveMessageResult receiveMessageResult) {
    List<MessageView> messageViews = receiveMessageResult.getMessageViews();
    String consumerGroup = request.getGroup().getName();
    for (MessageView messageView : messageViews) {
      VirtualFieldStore.setConsumerGroupByMessage(messageView, consumerGroup);
    }

    Context parentContext = Context.current();
    if (emitStableMessagingSemconv()) {
      // the process span parents to the context that the messages were received in, and links to
      // the producer context that the message headers point at; the receive span links to that
      // same producer context, and its own context is deliberately not handed to the process span
      for (MessageView messageView : messageViews) {
        VirtualFieldStore.setContextByMessage(messageView, parentContext);
      }
    }

    // Under stable/v3 semconv SimpleConsumer.receive is application-initiated, so an empty pull is
    // still span-eligible when receive spans are enabled, while a push consumer's internal poll
    // gets
    // a span only when it returned messages. In legacy semconv an empty pull never gets a span,
    // matching the behavior on main, so the application-initiated gate applies only under
    // stable/v3.
    // Metrics are recorded either way under stable/v3 semconv.
    boolean spanEligible =
        RocketMqSingletons.receiveSpansEnabled()
            && (!messageViews.isEmpty() || (emitStableMessagingSemconv() && pullApi));
    RocketMqReceiveRequest receiveRequest = RocketMqReceiveRequest.create(request, messageViews);
    Context context =
        MessagingReceiveTelemetry.record(
            consumerReceiveInstrumenter(),
            parentContext,
            receiveRequest,
            messageViews,
            null,
            timer,
            spanEligible);
    if (!emitStableMessagingSemconv() && context != null) {
      for (MessageView messageView : messageViews) {
        VirtualFieldStore.setContextByMessage(messageView, context);
      }
    }
  }

  @Override
  public void onFailure(Throwable throwable) {
    // A failed poll returned no messages. Under stable/v3 semconv only an application-initiated
    // receive is span-eligible; in legacy semconv a failed receive keeps a span whenever receive
    // spans are enabled, matching the behavior on main. Metrics are recorded either way under
    // stable/v3 semconv.
    boolean spanEligible =
        RocketMqSingletons.receiveSpansEnabled() && (!emitStableMessagingSemconv() || pullApi);
    Context parentContext = Context.current();
    RocketMqReceiveRequest receiveRequest = RocketMqReceiveRequest.create(request, emptyList());
    MessagingReceiveTelemetry.record(
        consumerReceiveInstrumenter(),
        parentContext,
        receiveRequest,
        null,
        throwable,
        timer,
        spanEligible);
  }
}
