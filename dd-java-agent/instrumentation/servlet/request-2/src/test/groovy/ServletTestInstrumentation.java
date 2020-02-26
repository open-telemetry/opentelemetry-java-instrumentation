import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.test.base.HttpServerTestAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class ServletTestInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        // Jetty 7.0
        .type(named("org.eclipse.jetty.server.HttpConnection"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    named("handleRequest"), HttpServerTestAdvice.ServerEntryAdvice.class.getName()))
        // Jetty 7.latest
        .type(named("org.eclipse.jetty.server.AbstractHttpConnection"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    named("headerComplete"),
                    HttpServerTestAdvice.ServerEntryAdvice.class.getName()));
  }
}
