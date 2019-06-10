// Modified by SignalFx
package datadog.trace.instrumentation.springdata;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.springdata.SpringDataDecorator.DECORATOR;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.data.repository.Repository;

@AutoService(Instrumenter.class)
public final class SpringDataProxyInstrumentation extends Instrumenter.Default {

  public SpringDataProxyInstrumentation() {
    super("spring-data");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("org.springframework.aop.framework.JdkDynamicAopProxy")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.DatabaseClientDecorator",
      "datadog.trace.agent.decorator.OrmClientDecorator",
      packageName + ".SpringDataDecorator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("invoke"))
            .and(
                takesArguments(3)
                    .and(takesArgument(0, Object.class))
                    .and(takesArgument(1, Method.class))
                    .and(takesArgument(2, Object[].class))),
        ProxyAdvice.class.getName());
  }

  public static class ProxyAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(value = 1, readOnly = false) Method invokedMethod) {
      final Class<?> clazz = invokedMethod.getDeclaringClass();
      // getting a proxy's target during type matching doesn't appear possible,
      // so we manually check the target method's class here.  As more spring functionality is
      // added, this should be broken out to ensure it's only done once to avoid unnecessary
      // overhead
      if (!Repository.class.isAssignableFrom(clazz)) {
        return null;
      }

      boolean isPublic = Modifier.isPublic(invokedMethod.getModifiers());
      if (!isPublic) {
        return null;
      }

      final Scope scope = GlobalTracer.get().buildSpan("proxy.query").startActive(true);
      final Span span = scope.span();
      DECORATOR.afterStart(span);
      DECORATOR.onOperation(span, invokedMethod);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      DECORATOR.onError(scope, throwable);
      DECORATOR.beforeFinish(scope);
      scope.close();
    }
  }
}
