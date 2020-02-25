import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Collection;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class NoOpInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder;
  }

  @Override
  public Collection<String> getLibraryBlacklistedPrefixes() {
    return Collections.emptySet();
  }
}
