/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JaxRsClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

@Priority(Priorities.HEADER_DECORATOR)
public class ClientTracingFilter implements ClientRequestFilter, ClientResponseFilter {
  public static final String CONTEXT_PROPERTY_NAME = "io.opentelemetry.javaagent.context";

  @Override
  public void filter(ClientRequestContext requestContext) {
    Context parentContext = Context.current();
    if (instrumenter().shouldStart(parentContext, requestContext)) {
      Context context = instrumenter().start(parentContext, requestContext);
      requestContext.setProperty(CONTEXT_PROPERTY_NAME, context);
    }
  }

  @Override
  public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
    Object contextObj = requestContext.getProperty(CONTEXT_PROPERTY_NAME);
    if (contextObj instanceof Context) {
      Context context = (Context) contextObj;
      instrumenter().end(context, requestContext, responseContext, null);
    }
    // we are done with this request, remove context so it could be gcd immediately in case request
    // context stays around for whatever reason
    requestContext.removeProperty(CONTEXT_PROPERTY_NAME);
  }
}
