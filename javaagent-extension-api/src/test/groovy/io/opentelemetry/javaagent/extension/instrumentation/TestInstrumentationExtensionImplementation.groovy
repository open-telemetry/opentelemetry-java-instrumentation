/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation

import io.opentelemetry.javaagent.extension.AgentExtensionTooling
import net.bytebuddy.agent.builder.AgentBuilder

class TestInstrumentationExtensionImplementation extends InstrumentationExtensionImplementation {
  @Override
  AgentBuilder extend(InstrumentationModule instrumentationModule, AgentBuilder parentAgentBuilder, AgentExtensionTooling tooling) {
    if (instrumentationModule instanceof InstrumentationModuleTest.TestInstrumentationModule) {
      instrumentationModule.applyCalled = true
    }
    return parentAgentBuilder
  }
}
