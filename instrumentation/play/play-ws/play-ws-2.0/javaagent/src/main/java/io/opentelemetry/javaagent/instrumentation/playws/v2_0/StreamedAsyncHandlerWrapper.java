/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Publisher;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;

public class StreamedAsyncHandlerWrapper<T> extends AsyncHandlerWrapper<T>
    implements StreamedAsyncHandler<T> {
  private final StreamedAsyncHandler<T> streamedDelegate;

  public StreamedAsyncHandlerWrapper(
      StreamedAsyncHandler<T> delegate, Request request, Context context, Context parentContext) {
    super(delegate, request, context, parentContext);
    streamedDelegate = delegate;
  }

  @Override
  public State onStream(Publisher<HttpResponseBodyPart> publisher) {
    try (Scope ignored = getParentContext().makeCurrent()) {
      return streamedDelegate.onStream(publisher);
    }
  }
}
