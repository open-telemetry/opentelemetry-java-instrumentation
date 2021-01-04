/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.bytebuddy;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.spi.ByteBuddyAgentCustomizer;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(ByteBuddyAgentCustomizer.class)
public class TestByteBuddyAgentCustomizer implements ByteBuddyAgentCustomizer {
  @Override
  public AgentBuilder customize(AgentBuilder agentBuilder) {
    return agentBuilder.with(TestAgentListener.INSTANCE);
  }
}
