package io.opentelemetry.auto.instrumentation.jaxrs2;

import static io.opentelemetry.auto.instrumentation.jaxrs2.JaxRsAnnotationsDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jaxrs2.JaxRsAnnotationsDecorator.TRACER;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
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
    public static SpanWithScope createGenericSpan(
        @Advice.This final ContainerRequestContext context) {

      if (context.getProperty(JaxRsAnnotationsDecorator.ABORT_HANDLED) == null) {
        final Span parent = TRACER.getCurrentSpan();
        final Span span = TRACER.spanBuilder("jax-rs.request.abort").startSpan();

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

        final SpanWithScope scope = new SpanWithScope(span, TRACER.withSpan(span));

        DECORATE.afterStart(span);
        DECORATE.onJaxRsSpan(span, parent, filterClass, method);

        return scope;
      }

      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanAndScope, @Advice.Thrown final Throwable throwable) {
      if (spanAndScope == null) {
        return;
      }

      final Span span = spanAndScope.getSpan();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
      }

      DECORATE.beforeFinish(span);
      span.end();
      spanAndScope.getScope().close();
    }
  }
}
