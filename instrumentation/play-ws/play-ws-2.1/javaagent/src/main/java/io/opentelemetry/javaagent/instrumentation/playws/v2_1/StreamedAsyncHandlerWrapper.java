/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_1;

import io.opentelemetry.context.Context;
import org.reactivestreams.Publisher;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;

public class StreamedAsyncHandlerWrapper<T> extends AsyncHandlerWrapper<T>
    implements StreamedAsyncHandler<T> {
  private final StreamedAsyncHandler<T> streamedDelegate;

  public StreamedAsyncHandlerWrapper(
      StreamedAsyncHandler<T> delegate, Context context, Context parentContext) {
    super(delegate, context, parentContext);
    streamedDelegate = delegate;
  }

  @Override
  public State onStream(Publisher<HttpResponseBodyPart> publisher) {
    return streamedDelegate.onStream(publisher);
  }
}
