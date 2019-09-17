package datadog.trace.instrumentation.springdata;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.springdata.SpringDataDecorator.DECORATOR;
import static net.bytebuddy.matcher.ElementMatchers.*;

@AutoService(Instrumenter.class)
public final class SpringTransactionInstrumentation extends Instrumenter.Default {

  public SpringTransactionInstrumentation() {
    super("spring-data");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(named("org.springframework.transaction.interceptor.TransactionInterceptor"));
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
        .and(takesArgument(0, named("org.aopalliance.intercept.MethodInvocation"))),
      QueryInterceptorAdvice.class.getName());
  }

  public static class QueryInterceptorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Argument(value = 0, readOnly = true) final MethodInvocation methodInvocation) {
      final Method invokedMethod = methodInvocation.getMethod();
      final Class<?> clazz = invokedMethod.getDeclaringClass();

      final Scope scope = GlobalTracer.get().buildSpan("repository.query").startActive(true);
      final Span span = scope.span();
      DECORATOR.afterStart(span);
      DECORATOR.onOperation(span, invokedMethod);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      DECORATOR.onError(scope, throwable);
      DECORATOR.beforeFinish(scope);
      scope.close();
    }
  }
}
