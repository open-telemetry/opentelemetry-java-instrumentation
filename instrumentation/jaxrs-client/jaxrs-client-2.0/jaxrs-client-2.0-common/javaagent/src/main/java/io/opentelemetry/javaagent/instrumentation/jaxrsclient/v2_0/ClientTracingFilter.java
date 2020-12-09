/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JaxRsClientTracer.tracer;

import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

@Priority(Priorities.HEADER_DECORATOR)
public class ClientTracingFilter implements ClientRequestFilter, ClientResponseFilter {
  public static final String OPERATION_PROPERTY_NAME =
      "io.opentelemetry.auto.jax-rs-client.operation";

  @Override
  public void filter(ClientRequestContext requestContext) {
    HttpClientOperation<ClientResponseContext> operation = tracer().startOperation(requestContext);
    requestContext.setProperty(OPERATION_PROPERTY_NAME, operation);
  }

  @Override
  public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
    Object operationObj = requestContext.getProperty(OPERATION_PROPERTY_NAME);
    if (operationObj instanceof HttpClientOperation) {
      @SuppressWarnings("unchecked")
      HttpClientOperation<ClientResponseContext> operation =
          (HttpClientOperation<ClientResponseContext>) operationObj;
      operation.end(responseContext);
    }
  }
}
