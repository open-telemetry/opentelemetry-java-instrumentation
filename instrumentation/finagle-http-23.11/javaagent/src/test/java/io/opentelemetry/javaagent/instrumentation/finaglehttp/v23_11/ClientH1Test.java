/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.finagle.Http;

class ClientH1Test extends AbstractClientTest {
  @Override
  protected Http.Client configureClient(Http.Client client) {
    return super.configureClient(client)
        // see note on AbstractClientTest
        .withNoHttp2();
  }
}
