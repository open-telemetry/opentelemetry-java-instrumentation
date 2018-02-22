package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.Utils.getConfigEnabled;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;

public interface Instrumenter {

  AgentBuilder instrument(AgentBuilder agentBuilder);

  @Slf4j
  abstract class Configurable implements Instrumenter {
    private final Set<String> instrumentationNames;
    protected final boolean enabled;

    public Configurable(final String instrumentationName, final String... additionalNames) {
      this.instrumentationNames = new HashSet(Arrays.asList(additionalNames));
      instrumentationNames.add(instrumentationName);

      // If default is enabled, we want to enable individually,
      // if default is disabled, we want to disable individually.
      final boolean defaultEnabled = defaultEnabled();
      boolean anyEnabled = defaultEnabled;
      for (final String name : instrumentationNames) {
        final boolean configEnabled =
            getConfigEnabled("dd.integration." + name + ".enabled", defaultEnabled);
        if (defaultEnabled) {
          anyEnabled &= configEnabled;
        } else {
          anyEnabled |= configEnabled;
        }
      }
      enabled = anyEnabled;
    }

    protected boolean defaultEnabled() {
      return getConfigEnabled("dd.integrations.enabled", true);
    }

    @Override
    public final AgentBuilder instrument(final AgentBuilder agentBuilder) {
      if (enabled) {
        return apply(agentBuilder);
      } else {
        log.debug("Instrumentation {} is disabled", this);
        return agentBuilder;
      }
    }

    protected abstract AgentBuilder apply(AgentBuilder agentBuilder);
  }
}
