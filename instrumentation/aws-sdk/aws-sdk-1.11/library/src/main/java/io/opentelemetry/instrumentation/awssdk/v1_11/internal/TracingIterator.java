/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import com.amazonaws.services.sqs.model.Message;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Iterator;
import javax.annotation.Nullable;

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

  private TracingIterator(Iterator<Message> delegateIterator, TracingList tracingList) {
    this.delegateIterator = delegateIterator;
    this.tracingList = tracingList;
  }

  static Iterator<Message> wrap(Iterator<Message> delegateIterator, TracingList tracingList) {
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

    // it's important not to suppress consumer span creation here using Instrumenter.shouldStart()
    // because this instrumentation can leak the context and so there may be a leaked consumer span
    // in the context, in which case it's important to overwrite the leaked span instead of
    // suppressing the correct span
    // (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1947)
    Message next = delegateIterator.next();
    if (next != null) {
      SqsMessage sqsMessage = SqsMessageImpl.wrap(next);
      Context parentContext = tracingList.getProcessParentContext();
      if (parentContext == null) {
        parentContext = sqsMessage.getCreationContext();
      }

      currentRequest = SqsProcessRequest.create(tracingList.getRequest(), sqsMessage);
      currentContext = tracingList.getInstrumenter().start(parentContext, currentRequest);
      currentScope = currentContext.makeCurrent();
    }
    return next;
  }

  private void closeScopeAndEndSpan() {
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

  @Override
  public void remove() {
    delegateIterator.remove();
  }
}
