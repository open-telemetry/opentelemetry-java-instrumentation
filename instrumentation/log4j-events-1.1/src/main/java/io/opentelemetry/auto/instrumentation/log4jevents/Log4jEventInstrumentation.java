package io.opentelemetry.auto.instrumentation.log4jevents;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.log4j.Category;
import org.apache.log4j.Priority;

@AutoService(Instrumenter.class)
public class Log4jEventInstrumentation extends Instrumenter.Default {
  public Log4jEventInstrumentation() {
    super("log4j-events");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.log4j.Category");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".Log4jEvents"};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(named("forcedLog"))
            .and(takesArguments(4))
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("org.apache.log4j.Priority")))
            .and(takesArgument(2, named("java.lang.Object")))
            .and(takesArgument(3, named("java.lang.Throwable"))),
        Log4jEventInstrumentation.class.getName() + "$ForcedLogAdvice");
    return transformers;
  }

  public static class ForcedLogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This final Category logger,
        @Advice.Argument(1) final Priority level,
        @Advice.Argument(2) final Object message,
        @Advice.Argument(3) final Throwable t) {
      Log4jEvents.capture(logger, level, message, t);
    }
  }
}
