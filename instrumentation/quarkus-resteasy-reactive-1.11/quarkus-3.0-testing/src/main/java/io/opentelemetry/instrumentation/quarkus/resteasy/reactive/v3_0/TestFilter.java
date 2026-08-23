/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive.v3_0;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

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
