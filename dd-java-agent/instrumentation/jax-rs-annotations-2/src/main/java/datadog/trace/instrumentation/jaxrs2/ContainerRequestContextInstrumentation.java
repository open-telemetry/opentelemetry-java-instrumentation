package datadog.trace.instrumentation.jaxrs2;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jaxrs2.JaxRsAnnotationsDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Create a generic jax-rs.request.abort span based on the class name of the filter Implementation
 * specifc instrumentations can override tag values
 */
@AutoService(Instrumenter.class)
public class ContainerRequestContextInstrumentation extends Instrumenter.Default {
  public ContainerRequestContextInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-filter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("javax.ws.rs.container.ContainerRequestContext")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("abortWith"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("javax.ws.rs.core.Response"))),
        ContainerRequestContextInstrumentation.class.getName() + "$ContainerRequestContextAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.tooling.ClassHierarchyIterable",
      "datadog.trace.agent.tooling.ClassHierarchyIterable$ClassIterator",
      "datadog.trace.instrumentation.jaxrs2.JaxRsAnnotationsDecorator",
    };
  }

  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope createGenericSpan(@Advice.This final ContainerRequestContext context) {

      if (context.getProperty(ContainerRequestFilterInstrumentation.ABORT_HANDLED) == null) {
        final AgentSpan parent = activeSpan();
        final AgentSpan span = startSpan("jax-rs.request.abort");

        // Save spans so a more specific instrumentation can run later
        context.setProperty(ContainerRequestFilterInstrumentation.ABORT_PARENT, parent);
        context.setProperty(ContainerRequestFilterInstrumentation.ABORT_SPAN, span);

        final Class filterClass =
            (Class) context.getProperty(ContainerRequestFilterInstrumentation.ABORT_FILTER_CLASS);
        Method method = null;
        try {
          method = filterClass.getMethod("filter", ContainerRequestContext.class);
        } catch (final NoSuchMethodException e) {
          // Unable to find the filter method.  This should not be reachable because the context
          // can only be aborted inside the filter method
        }

        final AgentScope scope = activateSpan(span, false);
        scope.setAsyncPropagation(true);

        DECORATE.afterStart(span);
        DECORATE.onAbort(span, parent, filterClass, method);

        return scope;
      }

      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
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
