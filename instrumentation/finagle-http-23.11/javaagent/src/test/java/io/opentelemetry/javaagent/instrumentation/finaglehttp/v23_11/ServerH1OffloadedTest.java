/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.finagle.Http;
import com.twitter.util.FuturePool;

class ServerH1OffloadedTest extends AbstractServerTest {
  @Override
  protected Http.Server configureServer(Http.Server in) {
    return in
        // ensures all work is offloaded to a thread pool -- this is where problems can happen
        .withExecutionOffloaded(FuturePool.unboundedPool())
        .withNoHttp2();
  }
}
