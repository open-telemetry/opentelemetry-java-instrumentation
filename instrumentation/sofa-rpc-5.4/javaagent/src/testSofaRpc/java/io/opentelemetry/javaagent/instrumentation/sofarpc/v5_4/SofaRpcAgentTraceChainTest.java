/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4;

import io.opentelemetry.instrumentation.sofarpc.v5_4.AbstractSofaRpcTraceChainTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class SofaRpcAgentTraceChainTest extends AbstractSofaRpcTraceChainTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean hasPeerService() {
    return true;
  }
}
