package test;

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
        // Tomcat
        .type(named("org.apache.catalina.connector.CoyoteAdapter"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(named("service"), HttpServerTestAdvice.ServerEntryAdvice.class.getName()));
  }
}
