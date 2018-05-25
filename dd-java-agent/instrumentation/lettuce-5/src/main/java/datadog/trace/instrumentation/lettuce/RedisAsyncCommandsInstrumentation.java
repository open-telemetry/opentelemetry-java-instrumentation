package datadog.trace.instrumentation.lettuce;

import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class RedisAsyncCommandsInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector REDIS_ASYNC_HELPERS =
      new HelperInjector(
          RedisAsyncCommandsInstrumentation.class.getPackage().getName() + ".RedisAsyncBiFunction");

  public RedisAsyncCommandsInstrumentation() {
    super("redis");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  protected AgentBuilder apply(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("io.lettuce.core.AbstractRedisAsyncCommands"))
        .transform(REDIS_ASYNC_HELPERS)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(named("dispatch"))
                        .and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
                    RedisAsyncCommandsAdvice.class.getName()))
        .asDecorator();
  }
}
