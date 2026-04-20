/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.util.FuturePool;
import org.junit.jupiter.api.extension.RegisterExtension;

class ClientH1OffloadedTest extends AbstractClientTest {

  @RegisterExtension
  static final FinagleClientExtension CLIENT =
      new FinagleClientExtension(
          // ensures all work is offloaded to a thread pool -- this is where problems can happen
          client -> client.withNoHttp2().withExecutionOffloaded(FuturePool.unboundedPool()));

  @Override
  protected FinagleClientExtension clientExtension() {
    return CLIENT;
  }
}
