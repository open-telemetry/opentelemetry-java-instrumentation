/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0.RocketMqSingletons.consumerProcessInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;

public class SimpleConsumerTracingIterator implements Iterator<MessageView> {

  private final Iterator<MessageView> delegate;
  @Nullable private MessageView currentMessage;
  @Nullable private Context currentContext;
  @Nullable private Scope currentScope;

  public static Iterator<MessageView> wrap(Iterator<MessageView> delegate) {
    return new SimpleConsumerTracingIterator(delegate);
  }

  private SimpleConsumerTracingIterator(Iterator<MessageView> delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean hasNext() {
    endCurrentProcess();
    return delegate.hasNext();
  }

  @Override
  public MessageView next() {
    endCurrentProcess();
    MessageView message = delegate.next();
    Context parentContext = VirtualFieldStore.getContextByMessage(message);
    if (parentContext == null) {
      parentContext = Context.current();
    }
    Instrumenter<MessageView, ConsumeResult> instrumenter = consumerProcessInstrumenter();
    if (instrumenter.shouldStart(parentContext, message)) {
      currentMessage = message;
      currentContext = instrumenter.start(parentContext, message);
      currentScope = currentContext.makeCurrent();
    }
    return message;
  }

  private void endCurrentProcess() {
    if (currentScope == null) {
      return;
    }
    currentScope.close();
    consumerProcessInstrumenter().end(currentContext, currentMessage, null, null);
    currentMessage = null;
    currentContext = null;
    currentScope = null;
  }

  @Override
  public void remove() {
    delegate.remove();
  }
}
