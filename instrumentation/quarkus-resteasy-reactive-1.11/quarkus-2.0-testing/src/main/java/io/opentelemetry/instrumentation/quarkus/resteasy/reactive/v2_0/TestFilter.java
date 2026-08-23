/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive.v2_0;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public class TestFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    if (containerRequestContext.getHeaderString("abort") != null) {
      containerRequestContext.abortWith(
          Response.status(Response.Status.UNAUTHORIZED)
              .entity("Aborted")
              .type(MediaType.TEXT_PLAIN_TYPE)
              .build());
    }
  }
}
