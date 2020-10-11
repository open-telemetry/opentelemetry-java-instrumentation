/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JaxRsClientTracer.TRACER;

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
  public void filter(ClientRequestContext requestContext) {
    Span span = TRACER.startSpan(requestContext);
    // TODO (trask) expose inject separate from startScope, e.g. for async cases
    Scope scope = TRACER.startScope(span, requestContext);
    scope.close();
    requestContext.setProperty(SPAN_PROPERTY_NAME, span);
  }

  @Override
  public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
    Object spanObj = requestContext.getProperty(SPAN_PROPERTY_NAME);
    if (spanObj instanceof Span) {
      Span span = (Span) spanObj;
      TRACER.end(span, responseContext);
    }
  }
}
