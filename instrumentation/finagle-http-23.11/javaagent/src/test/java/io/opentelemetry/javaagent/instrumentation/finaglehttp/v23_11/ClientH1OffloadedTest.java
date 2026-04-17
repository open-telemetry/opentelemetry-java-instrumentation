/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.finagle.Http;
import com.twitter.util.FuturePool;

class ClientH1OffloadedTest extends AbstractClientTest {
  @Override
  protected Http.Client configureClient(Http.Client client) {
    return super.configureClient(client)
        // ensures all work is offloaded to a thread pool -- this is where problems can happen
        .withExecutionOffloaded(FuturePool.unboundedPool())
        // see note on AbstractClientTest
        .withNoHttp2();
  }
}
