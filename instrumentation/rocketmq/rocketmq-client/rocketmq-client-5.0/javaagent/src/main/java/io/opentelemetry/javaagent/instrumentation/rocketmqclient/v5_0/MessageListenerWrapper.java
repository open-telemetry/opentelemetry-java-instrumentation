/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.message.MessageView;

public final class MessageListenerWrapper implements MessageListener {
  private final MessageListener delegator;

  public MessageListenerWrapper(MessageListener delegator) {
    this.delegator = delegator;
  }

  @Override
  public ConsumeResult consume(MessageView messageView) {
    Context parentContext = VirtualFieldStore.getContextByMessage(messageView);
    if (parentContext == null) {
      parentContext = Context.current();
    }
    Instrumenter<MessageView, ConsumeResult> processInstrumenter =
        RocketMqSingletons.consumerProcessInstrumenter();
    if (!processInstrumenter.shouldStart(parentContext, messageView)) {
      return delegator.consume(messageView);
    }
    Context context = processInstrumenter.start(parentContext, messageView);

    ConsumeResult consumeResult;
    try (Scope ignored = context.makeCurrent()) {
      consumeResult = delegator.consume(messageView);
    } catch (Throwable t) {
      processInstrumenter.end(context, messageView, null, t);
      throw t;
    }
    processInstrumenter.end(context, messageView, consumeResult, null);
    return consumeResult;
  }
}
