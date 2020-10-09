/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.playws.v2_1;

import io.opentelemetry.trace.Span;
import org.reactivestreams.Publisher;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;

public class StreamedAsyncHandlerWrapper extends AsyncHandlerWrapper
    implements StreamedAsyncHandler {
  private final StreamedAsyncHandler streamedDelegate;

  public StreamedAsyncHandlerWrapper(StreamedAsyncHandler delegate, Span span) {
    super(delegate, span);
    streamedDelegate = delegate;
  }

  @Override
  public State onStream(Publisher publisher) {
    return streamedDelegate.onStream(publisher);
  }
}
