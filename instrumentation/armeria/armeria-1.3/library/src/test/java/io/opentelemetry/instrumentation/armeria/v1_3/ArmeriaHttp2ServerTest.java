/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;

class ArmeriaHttp2ServerTest extends ArmeriaHttpServerTest {

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.useHttp2();
  }
}
