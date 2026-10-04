/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerInstrumenter {

  private final Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      singleProcessInstrumenter;
  private final Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      batchProcessInstrumenter;

  RocketMqConsumerInstrumenter(
      Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext> singleProcessInstrumenter,
      Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext> batchProcessInstrumenter) {
    this.singleProcessInstrumenter = singleProcessInstrumenter;
    this.batchProcessInstrumenter = batchProcessInstrumenter;
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
        return new ConsumerContext(context, request);
      }
      return null;
    }

    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(msgs, consumerGroup, batchSize, namespace);
    if (!batchProcessInstrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    Context context = batchProcessInstrumenter.start(parentContext, request);
    return new ConsumerContext(context, request);
  }

  void end(ConsumerContext consumerContext, ConsumeMessageContext response) {
    RocketMqConsumerRequest request = consumerContext.getRequest();
    if (request.getBatchSize() == 1) {
      singleProcessInstrumenter.end(consumerContext.getContext(), request, response, null);
      return;
    }
    batchProcessInstrumenter.end(consumerContext.getContext(), request, response, null);
  }

  static final class ConsumerContext {
    private final Context context;
    private final RocketMqConsumerRequest request;

    private ConsumerContext(Context context, RocketMqConsumerRequest request) {
      this.context = context;
      this.request = request;
    }

    Context getContext() {
      return context;
    }

    RocketMqConsumerRequest getRequest() {
      return request;
    }
  }
}
