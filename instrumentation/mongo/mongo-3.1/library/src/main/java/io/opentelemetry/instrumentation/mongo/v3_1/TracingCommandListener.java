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
  private final Map<Integer, Context> contextMap;
  private final Map<Integer, CommandStartedEvent> requestMap;

  TracingCommandListener(Instrumenter<CommandStartedEvent, Void> instrumenter) {
    this.instrumenter = instrumenter;
    this.contextMap = new ConcurrentHashMap<>();
    this.requestMap = new ConcurrentHashMap<>();
  }

  @Override
  public void commandStarted(CommandStartedEvent event) {
    Context parentContext = Context.current();
    if (instrumenter.shouldStart(parentContext, event)) {
      Context context = instrumenter.start(parentContext, event);
      contextMap.put(event.getRequestId(), context);
      requestMap.put(event.getRequestId(), event);
    }
  }

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    Context context = contextMap.remove(event.getRequestId());
    CommandStartedEvent request = requestMap.get(event.getRequestId());
    if (context != null && request != null) {
      instrumenter.end(context, request, null, null);
    }
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    Context context = contextMap.remove(event.getRequestId());
    CommandStartedEvent request = requestMap.get(event.getRequestId());
    if (context != null && request != null) {
      instrumenter.end(context, request, null, event.getThrowable());
    }
  }
}
