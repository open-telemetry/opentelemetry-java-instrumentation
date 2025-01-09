/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class LibraryJsonRpcTest extends AbstractJsonRpcTest {

  @RegisterExtension
  static InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected JsonRpcBasicServer configureServer(JsonRpcBasicServer server) {
    server.setInvocationListener(
        JsonRpcTelemetry.builder(testing.getOpenTelemetry()).build().newServerInvocationListener());
    return server;
  }
}
