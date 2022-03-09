/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3

import com.linecorp.armeria.server.ServerBuilder
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest

class ArmeriaHttpServerTest extends AbstractArmeriaHttpServerTest implements LibraryTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder sb) {
    return sb.decorator(ArmeriaTracing.builder(getOpenTelemetry())
      .setCapturedServerRequestHeaders([AbstractHttpServerTest.TEST_REQUEST_HEADER])
      .setCapturedServerResponseHeaders([AbstractHttpServerTest.TEST_RESPONSE_HEADER])
      .build()
      .newServiceDecorator())
  }
}
