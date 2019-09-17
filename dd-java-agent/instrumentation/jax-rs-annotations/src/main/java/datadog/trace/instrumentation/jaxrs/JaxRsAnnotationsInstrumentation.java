package datadog.trace.instrumentation.jaxrs;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.jaxrs.JaxRsAnnotationsDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JaxRsAnnotationsInstrumentation extends Instrumenter.Default {

  private static final String JAX_ENDPOINT_OPERATION_NAME = "jax-rs.request";

  public JaxRsAnnotationsInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-annotations");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        isAnnotatedWith(named("javax.ws.rs.Path"))
            .or(safeHasSuperType(declaresMethod(isAnnotatedWith(named("javax.ws.rs.Path"))))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator", packageName + ".JaxRsAnnotationsDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isAnnotatedWith(
            named("javax.ws.rs.Path")
                .or(named("javax.ws.rs.DELETE"))
                .or(named("javax.ws.rs.GET"))
                .or(named("javax.ws.rs.HEAD"))
                .or(named("javax.ws.rs.OPTIONS"))
                .or(named("javax.ws.rs.POST"))
                .or(named("javax.ws.rs.PUT"))),
        JaxRsAnnotationsAdvice.class.getName());
  }

  public static class JaxRsAnnotationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope nameSpan(@Advice.Origin final Method method) {
      final Tracer tracer = GlobalTracer.get();
      // Rename the parent span according to the path represented by these annotations.
      final Scope parent = tracer.scopeManager().active();
      final Scope scope = tracer.buildSpan(JAX_ENDPOINT_OPERATION_NAME).startActive(true);

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      DECORATE.onControllerStart(scope, parent, method);
      return DECORATE.afterStart(scope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      DECORATE.onError(scope, throwable);
      DECORATE.beforeFinish(scope);
      scope.close();
    }
  }
}
