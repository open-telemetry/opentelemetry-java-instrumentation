/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerInstrumenter {

  private final Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      singleProcessInstrumenter;
  // under the v1.43 conventions this covers the whole batch with a single span; under the old
  // conventions it covers one message of the batch
  private final Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      batchProcessInstrumenter;
  // only used under the old conventions, where it groups the per-message process spans
  private final Instrumenter<RocketMqConsumerRequest, Void> batchReceiveInstrumenter;

  RocketMqConsumerInstrumenter(
      Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext> singleProcessInstrumenter,
      Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext> batchProcessInstrumenter,
      Instrumenter<RocketMqConsumerRequest, Void> batchReceiveInstrumenter) {
    this.singleProcessInstrumenter = singleProcessInstrumenter;
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.batchReceiveInstrumenter = batchReceiveInstrumenter;
  }

  @Nullable
  ConsumerContext start(
      Context parentContext,
      List<MessageExt> msgs,
      String consumerGroup,
      @Nullable String namespace) {
    int batchSize = msgs.size();
    if (batchSize == 1) {
      RocketMqConsumerRequest request =
          new RocketMqConsumerRequest(msgs.get(0), consumerGroup, batchSize, namespace);
      if (singleProcessInstrumenter.shouldStart(parentContext, request)) {
        Context context = singleProcessInstrumenter.start(parentContext, request);
        return new ConsumerContext(context, request, false);
      }
      return null;
    }

    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(msgs, consumerGroup, batchSize, namespace);
    if (emitStableMessagingSemconv()) {
      // a single process span accounts for the whole batch and links to the creation context of
      // every message it accounts for
      if (!batchProcessInstrumenter.shouldStart(parentContext, request)) {
        return null;
      }
      Context context = batchProcessInstrumenter.start(parentContext, request);
      return new ConsumerContext(context, request, false);
    }

    boolean receiveStarted = batchReceiveInstrumenter.shouldStart(parentContext, request);
    Context receiveContext =
        receiveStarted ? batchReceiveInstrumenter.start(parentContext, request) : parentContext;
    boolean processStarted = false;
    for (MessageExt message : msgs) {
      processStarted |=
          createChildSpan(receiveContext, message, consumerGroup, batchSize, namespace);
    }
    if (receiveStarted || processStarted) {
      return new ConsumerContext(receiveContext, request, receiveStarted);
    }
    return null;
  }

  // rocketmq 4.8's ConsumeMessageHook only fires once per batch, so there is no per-message timing
  // to report; the per-message process spans of the old conventions are emitted as instantaneous
  // markers rather than all claiming the duration of the whole batch
  private boolean createChildSpan(
      Context parentContext,
      MessageExt msg,
      String consumerGroup,
      int batchSize,
      @Nullable String namespace) {
    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(msg, consumerGroup, batchSize, namespace);
    if (!batchProcessInstrumenter.shouldStart(parentContext, request)) {
      return false;
    }
    Context context = batchProcessInstrumenter.start(parentContext, request);
    batchProcessInstrumenter.end(context, request, null, null);
    return true;
  }

  void end(ConsumerContext consumerContext, ConsumeMessageContext response) {
    RocketMqConsumerRequest request = consumerContext.getRequest();
    if (request.getBatchSize() == 1) {
      singleProcessInstrumenter.end(consumerContext.getContext(), request, response, null);
      return;
    }
    if (emitStableMessagingSemconv()) {
      batchProcessInstrumenter.end(consumerContext.getContext(), request, response, null);
      return;
    }
    if (consumerContext.isReceiveStarted()) {
      batchReceiveInstrumenter.end(consumerContext.getContext(), request, null, null);
    }
  }

  static final class ConsumerContext {
    private final Context context;
    private final RocketMqConsumerRequest request;
    private final boolean receiveStarted;

    private ConsumerContext(
        Context context, RocketMqConsumerRequest request, boolean receiveStarted) {
      this.context = context;
      this.request = request;
      this.receiveStarted = receiveStarted;
    }

    Context getContext() {
      return context;
    }

    RocketMqConsumerRequest getRequest() {
      return request;
    }

    private boolean isReceiveStarted() {
      return receiveStarted;
    }
  }
}
