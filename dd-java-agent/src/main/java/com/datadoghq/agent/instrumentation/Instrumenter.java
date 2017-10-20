package com.datadoghq.agent.instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;

public interface Instrumenter {
  AgentBuilder instrument(AgentBuilder agentBuilder);
}
