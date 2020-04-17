package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.java.concurrent.AdviceUtils;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.context.TraceScope;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument {@link Runnable} and {@Callable} */
@Slf4j
@AutoService(Instrumenter.class)
public final class RunnableInstrumentation extends Instrumenter.Default {

  public RunnableInstrumentation() {
    super(AbstractExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named(Runnable.class.getName()));
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(Runnable.class.getName(), State.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("run").and(takesArguments(0)).and(isPublic()),
        RunnableInstrumentation.class.getName() + "$RunnableAdvice");
  }

  public static class RunnableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static TraceScope enter(@Advice.This final Runnable thiz) {
      final ContextStore<Runnable, State> contextStore =
          InstrumentationContext.get(Runnable.class, State.class);
      return AdviceUtils.startTaskScope(contextStore, thiz);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter final TraceScope scope) {
      AdviceUtils.endTaskScope(scope);
    }
  }
}
