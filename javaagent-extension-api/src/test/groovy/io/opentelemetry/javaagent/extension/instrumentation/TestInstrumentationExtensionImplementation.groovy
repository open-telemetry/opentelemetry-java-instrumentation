/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation


import net.bytebuddy.agent.builder.AgentBuilder

class TestInstrumentationExtensionImplementation extends InstrumentationExtensionImplementation {
  @Override
  AgentBuilder extend(InstrumentationModule instrumentationModule, AgentBuilder parentAgentBuilder) {
    if (instrumentationModule instanceof InstrumentationModuleTest.TestInstrumentationModule) {
      instrumentationModule.applyCalled = true
    }
    return parentAgentBuilder
  }
}
