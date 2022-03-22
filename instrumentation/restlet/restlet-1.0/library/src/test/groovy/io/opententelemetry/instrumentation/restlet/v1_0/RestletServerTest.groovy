/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_0

import com.noelios.restlet.StatusFilter
import io.opentelemetry.instrumentation.restlet.v1_0.AbstractRestletServerTest
import io.opentelemetry.instrumentation.restlet.v1_0.RestletTelemetry
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest
import org.restlet.Restlet

class RestletServerTest extends AbstractRestletServerTest implements LibraryTestTrait {

  @Override
  Restlet wrapRestlet(Restlet restlet, String path) {
    RestletTelemetry telemetry = RestletTelemetry.builder(openTelemetry)
      .setCapturedRequestHeaders([AbstractHttpServerTest.TEST_REQUEST_HEADER])
      .setCapturedResponseHeaders([AbstractHttpServerTest.TEST_RESPONSE_HEADER])
      .build()

    def tracingFilter = telemetry.newFilter(path)
    def statusFilter = new StatusFilter(component.getContext(), false, null, null)

    tracingFilter.setNext(statusFilter)
    statusFilter.setNext(restlet)

    return tracingFilter
  }

}
