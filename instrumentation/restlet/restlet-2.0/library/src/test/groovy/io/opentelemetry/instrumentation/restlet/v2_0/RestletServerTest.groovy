/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.restlet.Restlet
import org.restlet.engine.application.StatusFilter
import org.restlet.service.StatusService

class RestletServerTest extends AbstractRestletServerTest implements LibraryTestTrait {

  @Override
  Restlet wrapRestlet(Restlet restlet, String path) {

    RestletTracing tracing = RestletTracing.builder(openTelemetry)
      .captureHttpHeaders(capturedHttpHeadersForTesting())
      .build()

    def tracingFilter = tracing.newFilter(path)
    def statusFilter = new StatusFilter(component.getContext(), new StatusService())

    tracingFilter.setNext(statusFilter)
    statusFilter.setNext(restlet)

    return tracingFilter
  }

}
