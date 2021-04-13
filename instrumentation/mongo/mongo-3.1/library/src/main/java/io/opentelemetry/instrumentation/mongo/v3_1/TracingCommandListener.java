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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TracingCommandListener implements CommandListener {

  private final MongoClientTracer tracer;
  private final Map<Integer, Context> contextMap;

  TracingCommandListener(MongoClientTracer tracer) {
    this.tracer = tracer;
    contextMap = new ConcurrentHashMap<>();
  }

  @Override
  public void commandStarted(CommandStartedEvent event) {
    Context context = tracer.startSpan(Context.current(), event, event.getCommand());
    contextMap.put(event.getRequestId(), context);
  }

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    Context context = contextMap.remove(event.getRequestId());
    if (context != null) {
      tracer.end(context);
    }
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    Context context = contextMap.remove(event.getRequestId());
    if (context != null) {
      tracer.endExceptionally(context, event.getThrowable());
    }
  }
}
