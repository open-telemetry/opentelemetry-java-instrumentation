package datadog.trace.instrumentation.lettuce;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class LettuceAsyncCommandsInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector REDIS_ASYNC_HELPERS =
      new HelperInjector(
          LettuceAsyncCommandsInstrumentation.class.getPackage().getName()
              + ".LettuceAsyncBiFunction",
          LettuceAsyncCommandsInstrumentation.class.getPackage().getName()
              + ".LettuceInstrumentationUtil");

  public LettuceAsyncCommandsInstrumentation() {
    super("lettuce", "lettuce-5-async");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  protected AgentBuilder apply(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("io.lettuce.core.AbstractRedisAsyncCommands"),
            classLoaderHasClasses("io.lettuce.core.RedisClient"))
        .transform(REDIS_ASYNC_HELPERS)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(named("dispatch"))
                        .and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
                    LettuceAsyncCommandsAdvice.class.getName()))
        .asDecorator();
  }
}
