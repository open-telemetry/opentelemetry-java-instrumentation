/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.jaxrs.AbstractJaxRsFilterTest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

public abstract class JaxRsFilterTest<SERVER> extends AbstractJaxRsFilterTest<SERVER> {

  protected final SimpleRequestFilter simpleRequestFilter = new SimpleRequestFilter();
  protected final PrematchRequestFilter prematchRequestFilter = new PrematchRequestFilter();

  @Override
  protected void setAbortStatus(boolean abortNormal, boolean abortPrematch) {
    simpleRequestFilter.abort = abortNormal;
    prematchRequestFilter.abort = abortPrematch;
  }

  @Provider
  protected static class SimpleRequestFilter implements ContainerRequestFilter {
    boolean abort = false;

    @Override
    public void filter(ContainerRequestContext requestContext) {
      if (abort) {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity("Aborted")
                .type(MediaType.TEXT_PLAIN_TYPE)
                .build());
      }
    }
  }

  @Provider
  @PreMatching
  protected static class PrematchRequestFilter implements ContainerRequestFilter {
    boolean abort = false;

    @Override
    public void filter(ContainerRequestContext requestContext) {
      if (abort) {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity("Aborted Prematch")
                .type(MediaType.TEXT_PLAIN_TYPE)
                .build());
      }
    }
  }
}
