package datadog.trace.instrumentation.lettuce;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.lettuce.rx.LettuceFluxCreationAdvice;
import datadog.trace.instrumentation.lettuce.rx.LettuceMonoCreationAdvice;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class LettuceReactiveCommandsInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector REDIS_RX_HELPERS =
      new HelperInjector(
          LettuceReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".LettuceInstrumentationUtil",
          LettuceReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.LettuceMonoCreationAdvice",
          LettuceReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.LettuceMonoDualConsumer",
          LettuceReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.LettuceFluxCreationAdvice",
          LettuceReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.LettuceFluxTerminationRunnable",
          LettuceReactiveCommandsInstrumentation.class.getPackage().getName()
              + ".rx.LettuceFluxTerminationRunnable$FluxOnSubscribeConsumer");

  public LettuceReactiveCommandsInstrumentation() {
    super("lettuce", "lettuce-5-rx");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  protected AgentBuilder apply(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("io.lettuce.core.AbstractRedisReactiveCommands"),
            classLoaderHasClasses("io.lettuce.core.RedisClient"))
        .transform(REDIS_RX_HELPERS)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(named("createMono"))
                        .and(takesArgument(0, named("java.util.function.Supplier")))
                        .and(returns(named("reactor.core.publisher.Mono"))),
                    LettuceMonoCreationAdvice.class.getName())
                .advice(
                    isMethod()
                        .and(nameStartsWith("create"))
                        .and(nameEndsWith("Flux"))
                        .and(takesArgument(0, named("java.util.function.Supplier")))
                        .and(returns(named(("reactor.core.publisher.Flux")))),
                    LettuceFluxCreationAdvice.class.getName()))
        .asDecorator();
  }
}
