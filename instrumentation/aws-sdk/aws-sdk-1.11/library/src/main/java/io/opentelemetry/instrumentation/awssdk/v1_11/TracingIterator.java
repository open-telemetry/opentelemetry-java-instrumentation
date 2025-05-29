/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.services.sqs.model.Message;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Iterator;
import javax.annotation.Nullable;

class TracingIterator implements Iterator<Message> {

  private final Iterator<Message> delegateIterator;
  private final Instrumenter<SqsProcessRequest, Response<?>> instrumenter;
  private final Request<?> request;
  private final Response<?> response;
  private final Context receiveContext;

  /*
   * Note: this may potentially create problems if this iterator is used from different threads. But
   * at the moment we cannot do much about this.
   */
  @Nullable private SqsProcessRequest currentRequest;
  @Nullable private Context currentContext;
  @Nullable private Scope currentScope;

  private TracingIterator(
      Iterator<Message> delegateIterator,
      Instrumenter<SqsProcessRequest, Response<?>> instrumenter,
      Request<?> request,
      Response<?> response,
      Context receiveContext) {
    this.delegateIterator = delegateIterator;
    this.instrumenter = instrumenter;
    this.request = request;
    this.response = response;
    this.receiveContext = receiveContext;
  }

  public static Iterator<Message> wrap(
      Iterator<Message> delegateIterator,
      Instrumenter<SqsProcessRequest, Response<?>> instrumenter,
      Request<?> request,
      Response<?> response,
      Context receiveContext) {
    return new TracingIterator(delegateIterator, instrumenter, request, response, receiveContext);
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
      Context parentContext = receiveContext;
      if (parentContext == null) {
        parentContext = SqsParentContext.ofSystemAttributes(next.getAttributes());
      }

      currentRequest = SqsProcessRequest.create(request, SqsMessageImpl.wrap(next));
      currentContext = instrumenter.start(parentContext, currentRequest);
      currentScope = currentContext.makeCurrent();
    }
    return next;
  }

  private void closeScopeAndEndSpan() {
    if (currentScope != null) {
      currentScope.close();
      instrumenter.end(currentContext, currentRequest, response, null);
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
