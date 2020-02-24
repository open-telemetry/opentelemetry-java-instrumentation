package io.opentelemetry.auto.instrumentation.springscheduling;

import static io.opentelemetry.auto.instrumentation.springscheduling.SpringSchedulingDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springscheduling.SpringSchedulingDecorator.TRACER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
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
      "io.opentelemetry.auto.decorator.BaseDecorator",
      packageName + ".SpringSchedulingDecorator",
      getClass().getName() + "$RunnableWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor().and(takesArgument(0, Runnable.class)),
        SpringSchedulingInstrumentation.class.getName() + "$SpringSchedulingAdvice");
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

    @Override
    public void run() {
      final Span span = TRACER.spanBuilder("scheduled.call").startSpan();
      DECORATE.afterStart(span);

      try (final Scope scope = TRACER.withSpan(span)) {
        DECORATE.onRun(span, runnable);

        try {
          runnable.run();
        } catch (final Throwable throwable) {
          DECORATE.onError(span, throwable);
          throw throwable;
        }
      } finally {
        DECORATE.beforeFinish(span);
        span.end();
      }
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
