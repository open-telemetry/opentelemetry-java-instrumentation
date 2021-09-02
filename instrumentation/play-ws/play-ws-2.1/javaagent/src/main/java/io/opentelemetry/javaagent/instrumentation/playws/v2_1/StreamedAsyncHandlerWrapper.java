/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Publisher;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;

public class StreamedAsyncHandlerWrapper extends AsyncHandlerWrapper
    implements StreamedAsyncHandler {
  private final StreamedAsyncHandler streamedDelegate;

  public StreamedAsyncHandlerWrapper(
      StreamedAsyncHandler delegate, Request request, Context context, Context parentContext) {
    super(delegate, request, context, parentContext);
    streamedDelegate = delegate;
  }

  @Override
  public State onStream(Publisher publisher) {
    try (Scope ignored = getParentContext().makeCurrent()) {
      return streamedDelegate.onStream(publisher);
    }
  }
}
