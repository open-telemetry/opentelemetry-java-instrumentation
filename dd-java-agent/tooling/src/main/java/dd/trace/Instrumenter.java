package dd.trace;

import net.bytebuddy.agent.builder.AgentBuilder;

public interface Instrumenter {
  AgentBuilder instrument(AgentBuilder agentBuilder);
}
