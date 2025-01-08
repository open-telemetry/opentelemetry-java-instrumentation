/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_6;

import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_6.AbstractJsonRpcTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AgentJsonRpcTest extends AbstractJsonRpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected JsonRpcBasicServer configureServer(JsonRpcBasicServer server) {
    return server;
  }
}
