/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3

import com.linecorp.armeria.server.ServerBuilder
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class ArmeriaHttpServerTest extends AbstractArmeriaHttpServerTest implements LibraryTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder sb) {
    return sb.decorator(ArmeriaTracing.builder(getOpenTelemetry())
      .captureHttpServerHeaders(capturedHttpHeadersForTesting())
      .build()
      .newServiceDecorator())
  }
}
