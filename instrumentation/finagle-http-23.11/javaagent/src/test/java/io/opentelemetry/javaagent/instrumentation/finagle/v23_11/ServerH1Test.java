/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle.v23_11;

import com.twitter.finagle.Http;
import com.twitter.finagle.ListeningServer;

class ServerH1Test extends AbstractServerTest {
  @Override
  protected ListeningServer setupServer() {
    return Http.server()
        .withNoHttp2()
        .serve(address.getHost() + ":" + port, new AbstractServerTest.TestService());
  }
}
