/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11

import io.opentelemetry.instrumentation.test.LibraryTestTrait

class Aws1ClientTest extends AbstractAws1ClientTest implements LibraryTestTrait {
  @Override
  def configureClient(def client) {
    client.withRequestHandlers(
      AwsSdkTracing.builder(getOpenTelemetry())
        .setCaptureExperimentalSpanAttributes(true)
        .build()
        .newRequestHandler())
  }
}
