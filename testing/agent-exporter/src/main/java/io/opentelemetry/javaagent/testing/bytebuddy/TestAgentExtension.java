/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.bytebuddy;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(AgentExtension.class)
public class TestAgentExtension implements AgentExtension {

  @Override
  public AgentBuilder extend(AgentBuilder agentBuilder) {
    return agentBuilder.with(TestAgentListener.INSTANCE);
  }

  @Override
  public String extensionName() {
    return "test";
  }
}
