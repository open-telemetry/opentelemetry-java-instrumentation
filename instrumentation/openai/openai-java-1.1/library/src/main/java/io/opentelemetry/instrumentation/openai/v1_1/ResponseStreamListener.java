/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseStreamEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

final class ResponseStreamListener implements ChunkListener<ResponseStreamEvent> {

  private final Context context;
  private final ResponseCreateRequest request;
  private final Instrumenter<ResponseCreateRequest, Response> instrumenter;
  private final AtomicBoolean hasEnded = new AtomicBoolean();

  // The latest response snapshot carried by a lifecycle event. The terminal event (completed,
  // incomplete or failed) carries the status and usage; earlier events carry the id and model.
  @Nullable private volatile Response response;

  // Set by a typed error event, which the SDK delivers as an ordinary event rather than throwing.
  private volatile boolean errorEvent;

  ResponseStreamListener(
      Context context,
      ResponseCreateRequest request,
      Instrumenter<ResponseCreateRequest, Response> instrumenter) {
    this.context = context;
    this.request = request;
    this.instrumenter = instrumenter;
  }

  @Override
  public void onChunk(ResponseStreamEvent event) {
    if (event.isCreated()) {
      event.asCreated()._response().asKnown().ifPresent(r -> response = r);
    } else if (event.isInProgress()) {
      event.asInProgress()._response().asKnown().ifPresent(r -> response = r);
    } else if (event.isCompleted()) {
      event.asCompleted()._response().asKnown().ifPresent(r -> response = r);
    } else if (event.isIncomplete()) {
      event.asIncomplete()._response().asKnown().ifPresent(r -> response = r);
    } else if (event.isFailed()) {
      event.asFailed()._response().asKnown().ifPresent(r -> response = r);
    } else if (event.isError()) {
      errorEvent = true;
    }
  }

  @Override
  public void endSpan(@Nullable Throwable error) {
    // Use an atomic operation since close() type of methods are exposed to the user
    // and can come from any thread.
    if (!hasEnded.compareAndSet(false, true)) {
      return;
    }
    if (errorEvent && error == null) {
      Span.fromContext(context).setStatus(StatusCode.ERROR);
    }
    instrumenter.end(context, request, response, error);
  }
}
