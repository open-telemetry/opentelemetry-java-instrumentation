import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.test.base.HttpServerTestAdvice;
import io.opentelemetry.auto.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class NettyServerTestInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("io.netty.handler.codec.ByteToMessageDecoder"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    named("channelRead"), HttpServerTestAdvice.ServerEntryAdvice.class.getName()));
  }
}
