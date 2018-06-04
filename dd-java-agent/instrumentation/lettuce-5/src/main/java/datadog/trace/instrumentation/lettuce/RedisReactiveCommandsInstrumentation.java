package datadog.trace.instrumentation.lettuce;

import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.lettuce.rx.FluxCreationAdvice;
import datadog.trace.instrumentation.lettuce.rx.MonoCreationAdvice;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class RedisReactiveCommandsInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector REDIS_ASYNC_HELPERS =
      new HelperInjector(
          RedisReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".LettuceInstrumentationUtil",
          RedisReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.MonoCreationAdvice",
          RedisReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.MonoDualConsumer",
          RedisReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.FluxCreationAdvice",
          RedisReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.FluxTerminationCancellableRunnable",
          RedisReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.FluxTerminationCancellableRunnable$FluxOnSubscribeConsumer");

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
                        .and(takesArgument(0, named("java.util.function.Supplier")))
                        .and(returns(named("reactor.core.publisher.Mono"))),
                    MonoCreationAdvice.class.getName())
                .advice(
                    isMethod()
                        .and(nameStartsWith("create"))
                        .and(nameEndsWith("Flux"))
                        .and(takesArgument(0, named("java.util.function.Supplier")))
                        .and(returns(named(("reactor.core.publisher.Flux")))),
                    FluxCreationAdvice.class.getName()))
        .asDecorator();
  }
}
