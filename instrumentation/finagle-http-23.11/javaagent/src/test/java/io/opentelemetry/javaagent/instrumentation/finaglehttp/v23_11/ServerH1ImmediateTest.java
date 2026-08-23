/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.finagle.Http;
import com.twitter.util.FuturePool;

class ServerH1ImmediateTest extends ServerH1Test {
  @Override
  protected Http.Server configureServer(Http.Server in) {
    return super.configureServer(in)
        // ensures all work is single threaded (simple case)
        .withExecutionOffloaded(FuturePool.immediatePool());
  }
}
