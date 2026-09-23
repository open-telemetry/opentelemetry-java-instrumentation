/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Iterator;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import software.amazon.awssdk.services.sqs.model.Message;

class TracingIterator implements Iterator<Message> {

  private final Iterator<Message> delegateIterator;
  private final TracingList tracingList;

  /*
   * Note: this may potentially create problems if this iterator is used from different threads. But
   * at the moment we cannot do much about this.
   */
  @Nullable private SqsProcessRequest currentRequest;
  @Nullable private Context currentContext;
  @Nullable private Scope currentScope;

  TracingIterator(Iterator<Message> delegateIterator, TracingList tracingList) {
    this.delegateIterator = delegateIterator;
    this.tracingList = tracingList;
  }

  public static Iterator<Message> wrap(
      Iterator<Message> delegateIterator, TracingList tracingList) {
    return new TracingIterator(delegateIterator, tracingList);
  }

  @Override
  public boolean hasNext() {
    closeScopeAndEndSpan();
    return delegateIterator.hasNext();
  }

  @Override
  public Message next() {
    // in case they didn't call hasNext()...
    closeScopeAndEndSpan();

    Message next = delegateIterator.next();
    startProcessing(next);
    return next;
  }

  void startProcessing(Message message) {
    if (message != null && !tracingList.isProcessingOwnedOutsideSqsSdk()) {
      SqsMessage sqsMessage = tracingList.getTracingMessage(message);
      if (sqsMessage == null) {
        return;
      }
      Context parentContext = tracingList.getProcessParentContext();
      if (parentContext == null) {
        parentContext = sqsMessage.getCreationContext();
      }

      SqsProcessRequest request = SqsProcessRequest.create(tracingList.getRequest(), sqsMessage);
      if (shouldStartProcessing(tracingList, parentContext, request)) {
        currentRequest = request;
        currentContext = tracingList.getInstrumenter().start(parentContext, request);
        currentScope = currentContext.makeCurrent();
      }
    }
  }

  void closeScopeAndEndSpan() {
    if (currentScope != null) {
      currentScope.close();
      tracingList
          .getInstrumenter()
          .end(currentContext, currentRequest, tracingList.getResponse(), null);
      currentScope = null;
      currentRequest = null;
      currentContext = null;
    }
  }

  static void processCallback(
      TracingList tracingList, Message message, Consumer<? super Message> action) {
    requireNonNull(action);
    if (message == null || tracingList.isProcessingOwnedOutsideSqsSdk()) {
      action.accept(message);
      return;
    }

    SqsMessage sqsMessage = tracingList.getTracingMessage(message);
    if (sqsMessage == null) {
      action.accept(message);
      return;
    }

    Context parentContext = tracingList.getProcessParentContext();
    if (parentContext == null) {
      parentContext = sqsMessage.getCreationContext();
    }
    SqsProcessRequest request = SqsProcessRequest.create(tracingList.getRequest(), sqsMessage);
    if (!shouldStartProcessing(tracingList, parentContext, request)) {
      action.accept(message);
      return;
    }

    Context context = tracingList.getInstrumenter().start(parentContext, request);
    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      action.accept(message);
    } catch (Throwable t) {
      error = t;
      throw sneakyThrow(t);
    } finally {
      tracingList.getInstrumenter().end(context, request, tracingList.getResponse(), error);
    }
  }

  // The unchecked cast preserves callback failures that use a sneaky throw.
  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }

  private static boolean shouldStartProcessing(
      TracingList tracingList, Context parentContext, SqsProcessRequest request) {
    if (InstrumentationUtil.shouldSuppressInstrumentation(Context.current())) {
      return false;
    }

    // Iterator spans can leak if traversal is abandoned. Do not inherit ambient consumer
    // suppression when selecting another message, but retain the captured processing parent span.
    Context suppressionContext = Context.root().with(Span.fromContext(parentContext));
    return tracingList.getInstrumenter().shouldStart(suppressionContext, request);
  }

  @Override
  public void forEachRemaining(Consumer<? super Message> action) {
    requireNonNull(action);
    closeScopeAndEndSpan();
    delegateIterator.forEachRemaining(message -> processCallback(tracingList, message, action));
  }

  @Override
  public void remove() {
    delegateIterator.remove();
  }
}
