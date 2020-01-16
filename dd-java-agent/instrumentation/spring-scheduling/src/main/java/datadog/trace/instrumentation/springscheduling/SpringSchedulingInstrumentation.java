// This file includes software developed at SignalFx

package datadog.trace.instrumentation.springscheduling;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Trace;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class SpringSchedulingInstrumentation extends Instrumenter.Default {

  public SpringSchedulingInstrumentation() {
    super("spring-scheduling");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(named("org.springframework.scheduling.config.Task"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      packageName + ".SpringSchedulingDecorator",
      getClass().getName() + "$RunnableWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor().and(takesArgument(0, Runnable.class)),
        SpringSchedulingInstrumentation.class.getName() + "$RepositoryFactorySupportAdvice");
  }

  public static class SpringSchedulingAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onConstruction(
        @Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
      runnable = RunnableWrapper.wrapIfNeeded(runnable);
    }
  }

  public static class RunnableWrapper implements Runnable {
    private final Runnable runnable;

    private RunnableWrapper(final Runnable runnable) {
      this.runnable = runnable;
    }

    @Trace
    @Override
    public void run() {
      runnable.run();
    }

    public static Runnable wrapIfNeeded(final Runnable task) {
      // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
      // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
      if (task instanceof RunnableWrapper) {
        return task;
      }
      return new RunnableWrapper(task);
    }
  }
}
