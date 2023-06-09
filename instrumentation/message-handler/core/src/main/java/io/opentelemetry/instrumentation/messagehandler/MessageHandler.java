/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Collection;

public abstract class MessageHandler<T> {
  protected abstract Instrumenter<Collection<T>, Void> getMessageInstrumenter();

  protected abstract void doHandleMessages(Collection<T> messages);

  public void handleMessages(Collection<T> messages) {
    Context parentContext = Context.current();
    if (getMessageInstrumenter().shouldStart(parentContext, messages)) {
      io.opentelemetry.context.Context otelContext =
          getMessageInstrumenter().start(parentContext, messages);
      Throwable error = null;
      try (Scope ignored = otelContext.makeCurrent()) {
        doHandleMessages(messages);
      } catch (Throwable t) {
        error = t;
        throw t;
      } finally {
        getMessageInstrumenter().end(otelContext, messages, null, error);
      }
    } else {
      doHandleMessages(messages);
    }
  }
}
