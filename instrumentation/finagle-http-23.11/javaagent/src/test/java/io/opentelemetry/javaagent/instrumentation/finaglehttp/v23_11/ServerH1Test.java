/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.finagle.Http;

class ServerH1Test extends AbstractServerTest {
  @Override
  protected Http.Server configureServer(Http.Server in) {
    return in.withNoHttp2();
  }
}
