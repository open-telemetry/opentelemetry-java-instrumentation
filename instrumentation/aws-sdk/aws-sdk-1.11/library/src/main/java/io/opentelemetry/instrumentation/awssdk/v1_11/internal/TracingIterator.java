/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static java.util.Objects.requireNonNull;

import com.amazonaws.services.sqs.model.Message;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.ListIterator;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Best-effort processing spans end at the next iterator boundary or callback completion. Abandoned
 * traversal has no observable completion point, and an iterator's scope must stay on one thread.
 */
class TracingIterator implements ListIterator<Message> {

  private final ListIterator<Message> delegateIterator;
  private final TracingList tracingList;

  @Nullable private ProcessingInvocation currentInvocation;

  private TracingIterator(ListIterator<Message> delegateIterator, TracingList tracingList) {
    this.delegateIterator = delegateIterator;
    this.tracingList = tracingList;
  }

  static ListIterator<Message> wrap(
      ListIterator<Message> delegateIterator, TracingList tracingList) {
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

  @Override
  public boolean hasPrevious() {
    endCurrentInvocation();
    return delegateIterator.hasPrevious();
  }

  @Override
  public Message previous() {
    endCurrentInvocation();
    Message message = delegateIterator.previous();
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

  static void processCallback(
      TracingList tracingList, Message message, Consumer<? super Message> action) {
    requireNonNull(action);
    if (message == null) {
      action.accept(message);
      return;
    }

    ProcessingInvocation invocation =
        ProcessingInvocation.start(tracingList, SqsMessageImpl.wrap(message));
    try {
      action.accept(message);
    } catch (Throwable t) {
      if (invocation != null) {
        invocation.end(t);
      }
      throw t;
    }
    if (invocation != null) {
      invocation.end(null);
    }
  }

  @Override
  public void remove() {
    delegateIterator.remove();
  }

  @Override
  public int nextIndex() {
    return delegateIterator.nextIndex();
  }

  @Override
  public int previousIndex() {
    return delegateIterator.previousIndex();
  }

  @Override
  public void set(Message message) {
    delegateIterator.set(message);
  }

  @Override
  public void add(Message message) {
    delegateIterator.add(message);
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
      Context suppressionContext = Context.root().with(Span.fromContext(parentContext));
      if (!tracingList.getInstrumenter().shouldStart(suppressionContext, request)) {
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
