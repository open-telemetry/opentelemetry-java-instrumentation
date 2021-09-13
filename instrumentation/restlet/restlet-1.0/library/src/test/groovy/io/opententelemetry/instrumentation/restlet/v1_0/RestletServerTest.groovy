/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_0

import com.noelios.restlet.StatusFilter
import io.opentelemetry.instrumentation.restlet.v1_0.AbstractRestletServerTest
import io.opentelemetry.instrumentation.restlet.v1_0.RestletTracing
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.restlet.Restlet

class RestletServerTest extends AbstractRestletServerTest implements LibraryTestTrait {

  @Override
  Restlet wrapRestlet(Restlet restlet, String path){

    RestletTracing tracing = RestletTracing.create(openTelemetry)

    def tracingFilter = tracing.newFilter(path)
    def statusFilter = new StatusFilter(component.getContext(), false, null, null)

    tracingFilter.setNext(statusFilter)
    statusFilter.setNext(restlet)

    return tracingFilter
  }

}
