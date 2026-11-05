/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static java.util.Objects.requireNonNull;

import com.amazonaws.services.sqs.model.Message;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Iterator;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Best-effort processing spans end at the next iterator boundary or callback completion. Abandoned
 * traversal has no observable completion point, and an iterator's scope must stay on one thread.
 */
class TracingIterator implements Iterator<Message> {

  private final Iterator<Message> delegateIterator;
  private final TracingList tracingList;

  @Nullable private ProcessingInvocation currentInvocation;

  private TracingIterator(Iterator<Message> delegateIterator, TracingList tracingList) {
    this.delegateIterator = delegateIterator;
    this.tracingList = tracingList;
  }

  static Iterator<Message> wrap(Iterator<Message> delegateIterator, TracingList tracingList) {
    return new TracingIterator(delegateIterator, tracingList);
  }

  @Override
  public boolean hasNext() {
    endCurrentInvocation();
    return delegateIterator.hasNext();
  }

  @Override
  public Message next() {
    // in case they didn't call hasNext()...
    endCurrentInvocation();
    Message message = delegateIterator.next();
    startInvocation(message);
    return message;
  }

  private void startInvocation(Message message) {
    currentInvocation =
        message == null
            ? null
            : ProcessingInvocation.start(tracingList, SqsMessageImpl.wrap(message));
  }

  private void endCurrentInvocation() {
    endCurrentInvocation(null);
  }

  private void endCurrentInvocation(@Nullable Throwable error) {
    ProcessingInvocation invocation = currentInvocation;
    currentInvocation = null;
    if (invocation != null) {
      invocation.end(error);
    }
  }

  @Override
  public void forEachRemaining(Consumer<? super Message> action) {
    requireNonNull(action);
    while (hasNext()) {
      Message message = next();
      try {
        action.accept(message);
      } catch (Throwable t) {
        endCurrentInvocation(t);
        throw t;
      }
      endCurrentInvocation();
    }
  }

  @Override
  public void remove() {
    delegateIterator.remove();
  }

  private static final class ProcessingInvocation {
    private final TracingList tracingList;
    private final SqsProcessRequest request;
    private final Context context;
    private final Scope scope;

    @Nullable
    private static ProcessingInvocation start(TracingList tracingList, SqsMessage message) {
      if (tracingList.isProcessingOwnedOutsideSqsSdk()) {
        return null;
      }
      Context parentContext = tracingList.getProcessParentContext();
      if (parentContext == null) {
        parentContext = message.getCreationContext();
      }
      SqsProcessRequest request = SqsProcessRequest.create(tracingList.getRequest(), message);

      // An abandoned iterator can leave an ambient consumer span. Check suppression against the
      // captured parent so it cannot suppress unrelated raw processing.
      if (!tracingList.getInstrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = tracingList.getInstrumenter().start(parentContext, request);
      return new ProcessingInvocation(tracingList, request, context);
    }

    private ProcessingInvocation(
        TracingList tracingList, SqsProcessRequest request, Context context) {
      this.tracingList = tracingList;
      this.request = request;
      this.context = context;
      this.scope = context.makeCurrent();
    }

    private void end(@Nullable Throwable error) {
      scope.close();
      tracingList.getInstrumenter().end(context, request, tracingList.getResponse(), error);
    }
  }
}
