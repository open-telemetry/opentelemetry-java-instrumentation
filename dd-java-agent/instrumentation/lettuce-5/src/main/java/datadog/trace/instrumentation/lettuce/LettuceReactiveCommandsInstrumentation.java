package datadog.trace.instrumentation.lettuce;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.lettuce.rx.LettuceFluxCreationAdvice;
import datadog.trace.instrumentation.lettuce.rx.LettuceMonoCreationAdvice;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class LettuceReactiveCommandsInstrumentation extends Instrumenter.Default {

  public LettuceReactiveCommandsInstrumentation() {
    super("lettuce", "lettuce-5-rx");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher typeMatcher() {
    return named("io.lettuce.core.AbstractRedisReactiveCommands");
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return classLoaderHasClasses("io.lettuce.core.RedisClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
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
          + ".rx.LettuceFluxTerminationRunnable$FluxOnSubscribeConsumer"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("createMono"))
            .and(takesArgument(0, named("java.util.function.Supplier")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        LettuceMonoCreationAdvice.class.getName());
    transformers.put(
        isMethod()
            .and(nameStartsWith("create"))
            .and(nameEndsWith("Flux"))
            .and(takesArgument(0, named("java.util.function.Supplier")))
            .and(returns(named(("reactor.core.publisher.Flux")))),
        LettuceFluxCreationAdvice.class.getName());

    return transformers;
  }
}
