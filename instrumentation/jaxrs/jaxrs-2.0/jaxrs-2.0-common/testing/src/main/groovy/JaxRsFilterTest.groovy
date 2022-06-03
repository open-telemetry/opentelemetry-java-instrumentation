/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import spock.lang.Shared
import spock.lang.Unroll

import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.container.PreMatching
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.Provider

@Unroll
abstract class JaxRsFilterTest extends AbstractJaxRsFilterTest {

  @Shared
  SimpleRequestFilter simpleRequestFilter = new SimpleRequestFilter()

  @Shared
  PrematchRequestFilter prematchRequestFilter = new PrematchRequestFilter()

  @Override
  void setAbortStatus(boolean abortNormal, boolean abortPrematch) {
    simpleRequestFilter.abort = abortNormal
    prematchRequestFilter.abort = abortPrematch
  }

  @Provider
  static class SimpleRequestFilter implements ContainerRequestFilter {
    boolean abort = false

    @Override
    void filter(ContainerRequestContext requestContext) throws IOException {
      if (abort) {
        requestContext.abortWith(
          Response.status(Response.Status.UNAUTHORIZED)
            .entity("Aborted")
            .type(MediaType.TEXT_PLAIN_TYPE)
            .build())
      }
    }
  }

  @Provider
  @PreMatching
  static class PrematchRequestFilter implements ContainerRequestFilter {
    boolean abort = false

    @Override
    void filter(ContainerRequestContext requestContext) throws IOException {
      if (abort) {
        requestContext.abortWith(
          Response.status(Response.Status.UNAUTHORIZED)
            .entity("Aborted Prematch")
            .type(MediaType.TEXT_PLAIN_TYPE)
            .build())
      }
    }
  }
}
