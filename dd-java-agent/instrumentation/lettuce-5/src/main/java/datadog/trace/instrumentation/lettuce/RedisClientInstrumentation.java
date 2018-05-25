package datadog.trace.instrumentation.lettuce;

import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public final class RedisClientInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector REDIS_ASYNC_HELPERS =
      new HelperInjector(
          RedisAsyncCommandsInstrumentation.class.getPackage().getName() + ".RedisAsyncBiFunction");

  public RedisClientInstrumentation() {
    super("redis");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("io.lettuce.core.RedisClient"))
        .transform(DDTransformers.defaultTransformers())
        .transform(REDIS_ASYNC_HELPERS)
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isPrivate())
                        .and(returns(named("io.lettuce.core.ConnectionFuture")))
                        .and(nameStartsWith("connect"))
                        .and(nameEndsWith("Async"))
                        .and(takesArgument(1, named("io.lettuce.core.RedisURI"))),
                    ConnectionFutureAdvice.class.getName()))
        .asDecorator();
  }
}
