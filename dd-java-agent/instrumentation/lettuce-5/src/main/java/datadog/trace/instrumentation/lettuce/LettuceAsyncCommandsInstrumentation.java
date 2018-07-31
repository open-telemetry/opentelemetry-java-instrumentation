package datadog.trace.instrumentation.lettuce;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class LettuceAsyncCommandsInstrumentation extends Instrumenter.Default {

  public static final String PACKAGE =
      LettuceAsyncCommandsInstrumentation.class.getPackage().getName();

  public LettuceAsyncCommandsInstrumentation() {
    super("lettuce", "lettuce-5-async");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return named("io.lettuce.core.AbstractRedisAsyncCommands");
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return classLoaderHasClasses("io.lettuce.core.RedisClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      PACKAGE + ".LettuceAsyncBiFunction", PACKAGE + ".LettuceInstrumentationUtil"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("dispatch"))
            .and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        PACKAGE + ".LettuceAsyncCommandsAdvice");
    return transformers;
  }
}
