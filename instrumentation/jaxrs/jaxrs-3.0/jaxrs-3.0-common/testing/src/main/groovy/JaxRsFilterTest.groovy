/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import spock.lang.Shared
import spock.lang.Unroll

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
