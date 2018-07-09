package datadog.trace.instrumentation.lettuce;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class LettuceAsyncCommandsInstrumentation extends Instrumenter.Default {

  public LettuceAsyncCommandsInstrumentation() {
    super("lettuce", "lettuce-5-async");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
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
      LettuceAsyncCommandsInstrumentation.class.getPackage().getName() + ".LettuceAsyncBiFunction",
      LettuceAsyncCommandsInstrumentation.class.getPackage().getName()
          + ".LettuceInstrumentationUtil"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("dispatch"))
            .and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
        LettuceAsyncCommandsAdvice.class.getName());
    return transformers;
  }
}
