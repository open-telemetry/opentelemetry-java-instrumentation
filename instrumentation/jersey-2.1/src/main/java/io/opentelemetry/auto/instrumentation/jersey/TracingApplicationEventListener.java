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

import static io.opentelemetry.auto.instrumentation.jersey.JerseyDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.jersey.JerseyRequestExtractAdapter.GETTER;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

public class TracingApplicationEventListener implements ApplicationEventListener {

  @Override
  public void onEvent(ApplicationEvent event) {}

  @Override
  public RequestEventListener onRequest(RequestEvent requestEvent) {
    if (requestEvent.getType() != RequestEvent.Type.START) {
      return null;
    }

    ContainerRequest request = requestEvent.getContainerRequest();
    Span currentSpan = TRACER.getCurrentSpan();
    SpanContext remoteContext = JerseyDecorator.extract(request, GETTER);

    if (currentSpan.getContext().getTraceId().equals(remoteContext.getTraceId())) {
      // There is already active trace extracted from remote request.
      // This means this instrumentation is not the first one to handle this request.
      // Somebody, probably server-level instrumentation, has already started it.
      // In this case we do nothing
      return null;
    }

    return new TracingRequestEventListener(request, remoteContext);
  }
}
