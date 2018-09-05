package datadog.trace.instrumentation.lettuce;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class LettuceReactiveCommandsInstrumentation extends Instrumenter.Default {

  public static final String PACKAGE =
      LettuceReactiveCommandsInstrumentation.class.getPackage().getName();

  public LettuceReactiveCommandsInstrumentation() {
    super("lettuce", "lettuce-5-rx");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.AbstractRedisReactiveCommands");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      PACKAGE + ".LettuceInstrumentationUtil",
      PACKAGE + ".rx.LettuceMonoCreationAdvice",
      PACKAGE + ".rx.LettuceMonoDualConsumer",
      PACKAGE + ".rx.LettuceFluxCreationAdvice",
      PACKAGE + ".rx.LettuceFluxTerminationRunnable",
      PACKAGE + ".rx.LettuceFluxTerminationRunnable$FluxOnSubscribeConsumer"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("createMono"))
            .and(takesArgument(0, named("java.util.function.Supplier")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        PACKAGE + ".rx.LettuceMonoCreationAdvice");
    transformers.put(
        isMethod()
            .and(nameStartsWith("create"))
            .and(nameEndsWith("Flux"))
            .and(takesArgument(0, named("java.util.function.Supplier")))
            .and(returns(named(("reactor.core.publisher.Flux")))),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        PACKAGE + ".rx.LettuceFluxCreationAdvice");

    return transformers;
  }
}
