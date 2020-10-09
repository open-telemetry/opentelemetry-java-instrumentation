/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.mongo;

import static io.opentelemetry.instrumentation.auto.mongo.MongoClientTracer.TRACER;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.trace.Span;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TracingCommandListener implements CommandListener {

  private final Map<Integer, Span> spanMap = new ConcurrentHashMap<>();

  @Override
  public void commandStarted(CommandStartedEvent event) {
    Span span = TRACER.startSpan(event, event.getCommand());
    spanMap.put(event.getRequestId(), span);
  }

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    Span span = spanMap.remove(event.getRequestId());
    if (span != null) {
      TRACER.end(span);
    }
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    Span span = spanMap.remove(event.getRequestId());
    if (span != null) {
      TRACER.endExceptionally(span, event.getThrowable());
    }
  }
}
