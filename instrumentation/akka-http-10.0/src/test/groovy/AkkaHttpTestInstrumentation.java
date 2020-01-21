import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.test.base.HttpServerTestAdvice;
import io.opentelemetry.auto.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class AkkaHttpTestInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("akka.http.impl.engine.server.HttpServerBluePrint$PrepareRequests$$anon$1"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(named("onPush"), HttpServerTestAdvice.ServerEntryAdvice.class.getName()));
  }
}
