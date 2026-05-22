/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.finagle.Http;
import org.junit.jupiter.api.extension.RegisterExtension;

class ClientH1Test extends AbstractClientTest {

  @RegisterExtension
  static final FinagleClientExtension CLIENT =
      new FinagleClientExtension(
          // see note on AbstractClientTest about http/2
          Http.Client::withNoHttp2);

  @Override
  protected FinagleClientExtension clientExtension() {
    return CLIENT;
  }
}
