import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.test.base.HttpServerTestAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Collection;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class JettyTestInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        // Jetty 8
        .type(named("org.eclipse.jetty.server.AbstractHttpConnection"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    named("headerComplete"),
                    HttpServerTestAdvice.ServerEntryAdvice.class.getName()))
        // Jetty 9
        .type(named("org.eclipse.jetty.server.HttpChannel"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(named("handle"), HttpServerTestAdvice.ServerEntryAdvice.class.getName()));
  }

  @Override
  public Collection<String> getLibraryBlacklistedPrefixes() {
    return Collections.emptySet();
  }
}
