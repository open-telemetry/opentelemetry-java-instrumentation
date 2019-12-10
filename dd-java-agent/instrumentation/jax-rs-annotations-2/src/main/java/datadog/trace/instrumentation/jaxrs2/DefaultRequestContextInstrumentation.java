package datadog.trace.instrumentation.jaxrs2;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jaxrs2.JaxRsAnnotationsDecorator.DECORATE;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;

/**
 * Default context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>This default instrumentation uses the class name of the filter to create the span. More
 * specific instrumentations may override this value.
 */
@AutoService(Instrumenter.class)
public class DefaultRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope createGenericSpan(@Advice.This final ContainerRequestContext context) {

      if (context.getProperty(JaxRsAnnotationsDecorator.ABORT_HANDLED) == null) {
        final AgentSpan parent = activeSpan();
        final AgentSpan span = startSpan("jax-rs.request.abort");

        // Save spans so a more specific instrumentation can run later
        context.setProperty(JaxRsAnnotationsDecorator.ABORT_PARENT, parent);
        context.setProperty(JaxRsAnnotationsDecorator.ABORT_SPAN, span);

        final Class filterClass =
            (Class) context.getProperty(JaxRsAnnotationsDecorator.ABORT_FILTER_CLASS);
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
        DECORATE.onJaxRsSpan(span, parent, filterClass, method);

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
