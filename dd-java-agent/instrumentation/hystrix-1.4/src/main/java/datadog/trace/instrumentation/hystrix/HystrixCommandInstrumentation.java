package datadog.trace.instrumentation.hystrix;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.*;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class HystrixCommandInstrumentation extends Instrumenter.Default {

  public HystrixCommandInstrumentation() {
    super("hystrix");
  }

  @Override
  public ElementMatcher typeMatcher() {
    // Not adding a version restriction because this should work with any version and add some benefit.
    return not(isInterface()).and(hasSuperType(named("com.netflix.hystrix.HystrixCommand")));
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("run").or(named("getFallback"))), TraceAdvice.class.getName());
    return transformers;
  }

  public static class TraceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Origin final Method method) {
      final Class<?> declaringClass = method.getDeclaringClass();
      String className = declaringClass.getSimpleName();
      if (className.isEmpty()) {
        className = declaringClass.getName();
        if (declaringClass.getPackage() != null) {
          final String pkgName = declaringClass.getPackage().getName();
          if (!pkgName.isEmpty()) {
            className = declaringClass.getName().replace(pkgName, "").substring(1);
          }
        }
      }
      final String operationName = className + "." + method.getName();

      return GlobalTracer.get().buildSpan(operationName).startActive(true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      scope.close();
    }
  }
}
