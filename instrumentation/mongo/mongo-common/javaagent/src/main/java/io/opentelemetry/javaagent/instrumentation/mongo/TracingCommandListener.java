/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo;

import static io.opentelemetry.javaagent.instrumentation.mongo.MongoClientTracer.tracer;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.context.Context;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TracingCommandListener implements CommandListener {

  private final Map<Integer, Context> contextMap = new ConcurrentHashMap<>();

  @Override
  public void commandStarted(CommandStartedEvent event) {
    Context context = tracer().startOperation(Context.current(), event, event.getCommand());
    contextMap.put(event.getRequestId(), context);
  }

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    Context context = contextMap.remove(event.getRequestId());
    if (context != null) {
      tracer().end(context);
    }
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    Context context = contextMap.remove(event.getRequestId());
    if (context != null) {
      tracer().endExceptionally(context, event.getThrowable());
    }
  }
}
