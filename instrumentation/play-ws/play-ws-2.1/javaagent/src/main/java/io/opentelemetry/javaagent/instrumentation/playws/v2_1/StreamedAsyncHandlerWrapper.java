/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_1;

import io.opentelemetry.context.Context;
import org.reactivestreams.Publisher;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;

public class StreamedAsyncHandlerWrapper extends AsyncHandlerWrapper
    implements StreamedAsyncHandler {
  private final StreamedAsyncHandler streamedDelegate;

  public StreamedAsyncHandlerWrapper(
      StreamedAsyncHandler delegate, Context context, Context parentContext) {
    super(delegate, context, parentContext);
    streamedDelegate = delegate;
  }

  @Override
  public State onStream(Publisher publisher) {
    return streamedDelegate.onStream(publisher);
  }
}
