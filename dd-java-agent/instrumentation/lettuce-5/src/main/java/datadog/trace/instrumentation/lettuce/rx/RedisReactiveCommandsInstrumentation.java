package datadog.trace.instrumentation.lettuce.rx;

import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class RedisReactiveCommandsInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector REDIS_ASYNC_HELPERS =
      new HelperInjector(
          RedisReactiveCommandsInstrumentation.class.getPackage().getName() + ".MonoCreationAdvice",
          RedisReactiveCommandsInstrumentation.class.getPackage().getName() + ".MonoDualConsumer");

  public RedisReactiveCommandsInstrumentation() {
    super("redis");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  protected AgentBuilder apply(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("io.lettuce.core.AbstractRedisReactiveCommands"))
        .transform(REDIS_ASYNC_HELPERS)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(named("createMono"))
                        .and(takesArgument(0, named("java.util.function.Supplier"))),
                    MonoCreationAdvice.class.getName()))
        .asDecorator();
  }
}
