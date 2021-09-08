/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_0

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.restlet.v1_0.AbstractRestletServerTest
import io.opentelemetry.instrumentation.restlet.v1_0.RestletTracing
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.restlet.Restlet

class RestletServerTest extends AbstractRestletServerTest implements LibraryTestTrait {

  @Override
  Restlet wrapRestlet(Restlet restlet, String path){
    RestletTracing tracing = RestletTracing.create(openTelemetry)
    def filter = tracing.newFilter(path)
    filter.setNext(restlet)
    return filter
  }

  @Override
  boolean testException() {
    false //Filter's afterHandle does not execute if exception was thrown in the next restlet
  }
}
