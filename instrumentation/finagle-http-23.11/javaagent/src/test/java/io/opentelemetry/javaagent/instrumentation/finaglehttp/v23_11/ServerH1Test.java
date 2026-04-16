/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.finagle.Http;
import com.twitter.finagle.ListeningServer;
import com.twitter.util.FuturePool;

class ServerH1Test extends AbstractServerTest {
  @Override
  protected ListeningServer setupServer() {
    return Http.server()
        // ensures all work is offloaded to a thread pool -- this is where problems can happen
        .withExecutionOffloaded(FuturePool.unboundedPool())
        .withNoHttp2()
        .serve(address.getHost() + ":" + port, new AbstractServerTest.TestService());
  }
}
