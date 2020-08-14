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

package io.opentelemetry.instrumentation.auto.jaxrsclient.v2_0;

import static io.opentelemetry.instrumentation.auto.jaxrsclient.v2_0.JaxRsClientTracer.TRACER;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

@Priority(Priorities.HEADER_DECORATOR)
public class ClientTracingFilter implements ClientRequestFilter, ClientResponseFilter {
  public static final String SPAN_PROPERTY_NAME = "io.opentelemetry.auto.jax-rs-client.span";

  @Override
  public void filter(final ClientRequestContext requestContext) {
    Span span = TRACER.startSpan(requestContext);
    // TODO (trask) expose inject separate from startScope, e.g. for async cases
    Scope scope = TRACER.startScope(span, requestContext.getHeaders());
    scope.close();
    requestContext.setProperty(SPAN_PROPERTY_NAME, span);
  }

  @Override
  public void filter(
      final ClientRequestContext requestContext, final ClientResponseContext responseContext) {
    Object spanObj = requestContext.getProperty(SPAN_PROPERTY_NAME);
    if (spanObj instanceof Span) {
      Span span = (Span) spanObj;
      TRACER.end(span, responseContext);
    }
  }
}
