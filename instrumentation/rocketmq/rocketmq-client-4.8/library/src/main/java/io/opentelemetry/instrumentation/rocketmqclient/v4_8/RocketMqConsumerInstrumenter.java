/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerInstrumenter {

  private final Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      singleProcessInstrumenter;
  private final Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext>
      batchProcessInstrumenter;
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
    if (msgs.size() == 1) {
      RocketMqConsumerRequest request =
          new RocketMqConsumerRequest(msgs.get(0), consumerGroup, batchSize, namespace);
      if (singleProcessInstrumenter.shouldStart(parentContext, request)) {
        Context context = singleProcessInstrumenter.start(parentContext, request);
        return new ConsumerContext(context, request, emptyList(), false);
      }
    } else {
      RocketMqConsumerRequest request =
          new RocketMqConsumerRequest(msgs.get(0), consumerGroup, batchSize, namespace);
      boolean receiveStarted = batchReceiveInstrumenter.shouldStart(parentContext, request);
      Context receiveContext =
          receiveStarted ? batchReceiveInstrumenter.start(parentContext, request) : parentContext;
      Context processParentContext = emitStableMessagingSemconv() ? parentContext : receiveContext;
      List<StartedProcessSpan> processSpans = new ArrayList<>(batchSize);
      for (MessageExt message : msgs) {
        createChildSpan(
            processParentContext, message, consumerGroup, batchSize, namespace, processSpans);
      }
      if (receiveStarted || !processSpans.isEmpty()) {
        return new ConsumerContext(receiveContext, request, processSpans, receiveStarted);
      }
    }
    return null;
  }

  private void createChildSpan(
      Context parentContext,
      MessageExt msg,
      String consumerGroup,
      int batchSize,
      @Nullable String namespace,
      List<StartedProcessSpan> processSpans) {
    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(msg, consumerGroup, batchSize, namespace);
    if (batchProcessInstrumenter.shouldStart(parentContext, request)) {
      Context context = batchProcessInstrumenter.start(parentContext, request);
      processSpans.add(new StartedProcessSpan(context, request));
    }
  }

  void end(ConsumerContext consumerContext, ConsumeMessageContext response) {
    if (consumerContext.getRequest().getBatchSize() == 1) {
      singleProcessInstrumenter.end(
          consumerContext.getContext(), consumerContext.getRequest(), response, null);
    } else {
      for (StartedProcessSpan processSpan : consumerContext.getProcessSpans()) {
        batchProcessInstrumenter.end(
            processSpan.getContext(), processSpan.getRequest(), response, null);
      }
      if (consumerContext.isReceiveStarted()) {
        batchReceiveInstrumenter.end(
            consumerContext.getContext(), consumerContext.getRequest(), null, null);
      }
    }
  }

  static final class ConsumerContext {
    private final Context context;
    private final RocketMqConsumerRequest request;
    private final List<StartedProcessSpan> processSpans;
    private final boolean receiveStarted;

    private ConsumerContext(
        Context context,
        RocketMqConsumerRequest request,
        List<StartedProcessSpan> processSpans,
        boolean receiveStarted) {
      this.context = context;
      this.request = request;
      this.processSpans = processSpans;
      this.receiveStarted = receiveStarted;
    }

    Context getContext() {
      return context;
    }

    RocketMqConsumerRequest getRequest() {
      return request;
    }

    private List<StartedProcessSpan> getProcessSpans() {
      return processSpans;
    }

    private boolean isReceiveStarted() {
      return receiveStarted;
    }
  }

  private static final class StartedProcessSpan {
    private final Context context;
    private final RocketMqConsumerRequest request;

    private StartedProcessSpan(Context context, RocketMqConsumerRequest request) {
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
