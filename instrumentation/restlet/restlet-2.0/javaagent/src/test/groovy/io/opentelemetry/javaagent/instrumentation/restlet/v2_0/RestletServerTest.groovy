/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_0

import io.opentelemetry.instrumentation.restlet.v2_0.AbstractRestletServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND

class RestletServerTest extends AbstractRestletServerTest implements AgentTestTrait {

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    switch (endpoint) {
      case NOT_FOUND:
        return getContextPath() + "/"
      default:
        return super.expectedHttpRoute(endpoint, method)
    }
  }

  @Override
  boolean hasResponseCustomizer(ServerEndpoint endpoint) {
    return true
  }

}
