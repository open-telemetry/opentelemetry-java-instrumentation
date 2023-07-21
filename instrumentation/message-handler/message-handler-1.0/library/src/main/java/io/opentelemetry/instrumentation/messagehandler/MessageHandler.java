/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public abstract class MessageHandler<INPUT, OUTPUT> {

  protected abstract Instrumenter<INPUT, OUTPUT> getMessageInstrumenter();

  protected abstract OUTPUT doHandle(INPUT request);

  public OUTPUT handle(INPUT request) {
    Instrumenter<INPUT, OUTPUT> instrumenter = getMessageInstrumenter();
    OUTPUT response = null;
    Throwable error = null;

    Context context = instrumenter.start(Context.current(), request);

    try (Scope scope = context.makeCurrent()) {
      response = doHandle(request);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      instrumenter.end(context, request, response, error);
    }

    return response;
  }
}
