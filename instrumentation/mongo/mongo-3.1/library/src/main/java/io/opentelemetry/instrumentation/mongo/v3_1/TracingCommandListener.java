/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TracingCommandListener implements CommandListener {

  private final Instrumenter<CommandStartedEvent, Void> instrumenter;
  private final Map<Integer, ContextAndRequest> requestMap;

  TracingCommandListener(Instrumenter<CommandStartedEvent, Void> instrumenter) {
    this.instrumenter = instrumenter;
    this.requestMap = new ConcurrentHashMap<>();
  }

  @Override
  public void commandStarted(CommandStartedEvent event) {
    Context parentContext = Context.current();
    if (instrumenter.shouldStart(parentContext, event)) {
      Context context = instrumenter.start(parentContext, event);
      requestMap.put(event.getRequestId(), ContextAndRequest.create(context, event));
    }
  }

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    ContextAndRequest contextAndRequest = requestMap.remove(event.getRequestId());
    if (contextAndRequest != null) {
      instrumenter.end(contextAndRequest.getContext(), contextAndRequest.getRequest(), null, null);
    }
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    ContextAndRequest contextAndRequest = requestMap.remove(event.getRequestId());
    if (contextAndRequest != null) {
      instrumenter.end(
          contextAndRequest.getContext(),
          contextAndRequest.getRequest(),
          null,
          event.getThrowable());
    }
  }
}
