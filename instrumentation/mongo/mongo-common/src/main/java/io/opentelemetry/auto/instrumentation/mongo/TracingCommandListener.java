/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.mongo;

import static io.opentelemetry.auto.instrumentation.mongo.MongoClientTracer.TRACER;

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
  public void commandStarted(final CommandStartedEvent event) {
    Span span = TRACER.startSpan(event, event.getCommand());
    spanMap.put(event.getRequestId(), span);
  }

  @Override
  public void commandSucceeded(final CommandSucceededEvent event) {
    final Span span = spanMap.remove(event.getRequestId());
    if (span != null) {
      TRACER.end(span);
    }
  }

  @Override
  public void commandFailed(final CommandFailedEvent event) {
    final Span span = spanMap.remove(event.getRequestId());
    if (span != null) {
      TRACER.endExceptionally(span, event.getThrowable());
    }
  }
}
