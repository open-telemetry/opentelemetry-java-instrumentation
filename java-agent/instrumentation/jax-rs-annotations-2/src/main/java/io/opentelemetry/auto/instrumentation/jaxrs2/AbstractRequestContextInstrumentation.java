package io.opentelemetry.auto.instrumentation.jaxrs2;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.jaxrs2.JaxRsAnnotationsDecorator.DECORATE;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class AbstractRequestContextInstrumentation extends Instrumenter.Default {
  public AbstractRequestContextInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-filter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("javax.ws.rs.container.ContainerRequestContext")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable",
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsDecorator",
      AbstractRequestContextInstrumentation.class.getName() + "$RequestFilterHelper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("abortWith"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("javax.ws.rs.core.Response"))),
        getClass().getName() + "$ContainerRequestContextAdvice");
  }

  public static class RequestFilterHelper {
    public static AgentScope createOrUpdateAbortSpan(
        final ContainerRequestContext context, final Class resourceClass, final Method method) {

      if (method != null && resourceClass != null) {
        context.setProperty(JaxRsAnnotationsDecorator.ABORT_HANDLED, true);
        // The ordering of the specific and general abort instrumentation is unspecified
        // The general instrumentation (ContainerRequestFilterInstrumentation) saves spans
        // properties if it ran first
        AgentSpan parent = (AgentSpan) context.getProperty(JaxRsAnnotationsDecorator.ABORT_PARENT);
        AgentSpan span = (AgentSpan) context.getProperty(JaxRsAnnotationsDecorator.ABORT_SPAN);

        if (span == null) {
          parent = activeSpan();
          span = startSpan("jax-rs.request.abort");

          final AgentScope scope = activateSpan(span, false);

          DECORATE.afterStart(span);
          DECORATE.onJaxRsSpan(span, parent, resourceClass, method);

          return scope;
        } else {
          DECORATE.onJaxRsSpan(span, parent, resourceClass, method);
          return null;
        }
      } else {
        return null;
      }
    }

    public static void closeSpanAndScope(final AgentScope scope, final Throwable throwable) {
      if (scope == null) {
        return;
      }

      final AgentSpan span = scope.span();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
      }

      DECORATE.beforeFinish(span);
      span.finish();
      scope.close();
    }
  }
}
