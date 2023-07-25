/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Collection;

public abstract class MessageHandler<INPUT> {

  protected abstract Instrumenter<Collection<INPUT>, Void> getMessageInstrumenter();

  protected abstract void doHandle(Collection<INPUT> request);

  public void handle(Collection<INPUT> request) {
    Instrumenter<Collection<INPUT>, Void> instrumenter = getMessageInstrumenter();
    Throwable error = null;

    Context context = instrumenter.start(Context.current(), request);

    try (Scope scope = context.makeCurrent()) {
      doHandle(request);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      instrumenter.end(context, request, null, error);
    }
  }
}
