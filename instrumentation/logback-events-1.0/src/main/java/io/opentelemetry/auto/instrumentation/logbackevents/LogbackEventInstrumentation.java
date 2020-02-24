package io.opentelemetry.auto.instrumentation.logbackevents;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
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
import unshaded.ch.qos.logback.classic.spi.ILoggingEvent;

@AutoService(Instrumenter.class)
public class LogbackEventInstrumentation extends Instrumenter.Default {
  public LogbackEventInstrumentation() {
    super("logback-events");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("unshaded.ch.qos.logback.classic.Logger");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".LogbackEvents"};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("callAppenders"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("unshaded.ch.qos.logback.classic.spi.ILoggingEvent"))),
        LogbackEventInstrumentation.class.getName() + "$CallAppendersAdvice");
    return transformers;
  }

  public static class CallAppendersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.Argument(0) final ILoggingEvent event) {
      LogbackEvents.capture(event);
    }
  }
}
