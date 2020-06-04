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
package io.opentelemetry.auto.instrumentation.jersey;

import static io.opentelemetry.auto.instrumentation.jersey.JerseyDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jersey.JerseyDecorator.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Builder;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.internal.process.MappableException;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

@Slf4j
public class TracingRequestEventListener implements RequestEventListener {

  private final SpanWithScope scope;

  public TracingRequestEventListener(ContainerRequest request, SpanContext remoteContext) {
    // We are the first who tried to extract remote context
    // This means we are the entry point of a remote request in this JVM.
    Builder builder = TRACER.spanBuilder(DECORATE.spanNameForRequest(request));
    builder.setParent(remoteContext);
    builder.setSpanKind(Kind.SERVER);

    final Span span = builder.startSpan();

    DECORATE.onConnection(span, request);
    DECORATE.onRequest(span, request);

    scope = new SpanWithScope(span, currentContextWith(span));
  }

  @Override
  public void onEvent(RequestEvent event) {
    final Span span = scope.getSpan();

    switch (event.getType()) {
      case ON_EXCEPTION:
        Throwable eventException = event.getException();
        if (eventException instanceof MappableException) {
          eventException = eventException.getCause();
        }

        if (!(eventException instanceof NotFoundException)) {
          DECORATE.onError(span, eventException);
        }

        break;
      case FINISHED:
        ContainerResponse response = event.getContainerResponse();
        // Unmapped exceptions are rethrown to the container
        // and responses, if any, are created there.
        if (response != null) {
          DECORATE.onResponse(span, response);
        }
        scope.closeScope();
        span.end();
    }
  }
}
