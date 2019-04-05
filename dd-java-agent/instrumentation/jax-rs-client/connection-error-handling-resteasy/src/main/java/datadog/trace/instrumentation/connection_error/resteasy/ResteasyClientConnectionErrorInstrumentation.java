package datadog.trace.instrumentation.connection_error.resteasy;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.jaxrs.ClientTracingFilter;
import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;

/**
 * JAX-RS Client API doesn't define a good point where we can handle connection failures, so we must
 * handle these errors at the implementation level.
 */
@AutoService(Instrumenter.class)
public final class ResteasyClientConnectionErrorInstrumentation extends Instrumenter.Default {

  public ResteasyClientConnectionErrorInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.resteasy.client.jaxrs.internal.ClientInvocation");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      getClass().getName() + "$WrappedFuture",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(isMethod().and(isPublic()).and(named("invoke")), InvokeAdvice.class.getName());
    transformers.put(
        isMethod().and(isPublic()).and(named("submit")).and(returns(Future.class)),
        SubmitAdvice.class.getName());
    return transformers;
  }

  public static class InvokeAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleError(
        @Advice.FieldValue("configuration") final ClientConfiguration context,
        @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          final Span span = (Span) prop;
          Tags.ERROR.set(span, true);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
          span.finish();
        }
      }
    }
  }

  public static class SubmitAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void handleError(
        @Advice.FieldValue("configuration") final ClientConfiguration context,
        @Advice.Return(readOnly = false) Future<?> future) {
      if (!(future instanceof WrappedFuture)) {
        future = new WrappedFuture<>(future, context);
      }
    }
  }

  public static class WrappedFuture<T> implements Future<T> {

    private final Future<T> wrapped;
    private final ClientConfiguration context;

    public WrappedFuture(final Future<T> wrapped, final ClientConfiguration context) {
      this.wrapped = wrapped;
      this.context = context;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return wrapped.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return wrapped.isCancelled();
    }

    @Override
    public boolean isDone() {
      return wrapped.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      try {
        return wrapped.get();
      } catch (final ExecutionException e) {
        final Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          final Span span = (Span) prop;
          Tags.ERROR.set(span, true);
          span.log(Collections.singletonMap(Fields.ERROR_OBJECT, e.getCause()));
          span.finish();
        }
        throw e;
      }
    }

    @Override
    public T get(final long timeout, final TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      try {
        return wrapped.get(timeout, unit);
      } catch (final ExecutionException e) {
        final Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          final Span span = (Span) prop;
          Tags.ERROR.set(span, true);
          span.log(Collections.singletonMap(Fields.ERROR_OBJECT, e.getCause()));
          span.finish();
        }
        throw e;
      }
    }
  }
}
