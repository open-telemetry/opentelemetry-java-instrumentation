package io.opentelemetry.javaagent.testing.bytebuddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.spi.ByteBuddyAgentCustomizer;

@AutoService(ByteBuddyAgentCustomizer.class)
public class TestByteBuddyAgentCustomizer implements ByteBuddyAgentCustomizer {
  @Override
  public AgentBuilder customize(AgentBuilder agentBuilder) {
    return agentBuilder.with(TestAgentListener.INSTANCE);
  }
}
