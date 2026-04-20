/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.util.FuturePool;
import org.junit.jupiter.api.extension.RegisterExtension;

class ClientH1ImmediateTest extends AbstractClientTest {

  @RegisterExtension
  static final FinagleClientExtension CLIENT =
      new FinagleClientExtension(
          // ensures all work is single threaded (simple case)
          client -> client.withNoHttp2().withExecutionOffloaded(FuturePool.immediatePool()));

  @Override
  protected FinagleClientExtension clientExtension() {
    return CLIENT;
  }
}
