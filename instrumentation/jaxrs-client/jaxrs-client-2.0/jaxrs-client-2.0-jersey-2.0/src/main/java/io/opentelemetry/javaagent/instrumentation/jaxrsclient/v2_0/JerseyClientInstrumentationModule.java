/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JaxRsClientTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.jersey.client.ClientRequest;

/**
 * JAX-RS Client API doesn't define a good point where we can handle connection failures, so we must
 * handle these errors at the implementation level.
 */
@AutoService(InstrumentationModule.class)
public final class JerseyClientInstrumentationModule extends InstrumentationModule {

  public JerseyClientInstrumentationModule() {
    super("jaxrs", "jaxrs-2.0", "jaxrs-client", "jersey-client", "jersey-client-2.0");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      getClass().getName() + "$WrappedFuture",
      packageName + ".JaxRsClientTracer",
      packageName + ".InjectAdapter",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new JerseyClientConnectionErrorInstrumentation());
  }

  private static final class JerseyClientConnectionErrorInstrumentation
      implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.glassfish.jersey.client.JerseyInvocation");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

      transformers.put(
          isMethod().and(isPublic()).and(named("invoke")),
          JerseyClientInstrumentationModule.class.getName() + "$InvokeAdvice");

      transformers.put(
          isMethod().and(isPublic()).and(named("submit")).and(returns(Future.class)),
          JerseyClientInstrumentationModule.class.getName() + "$SubmitAdvice");

      return transformers;
    }
  }

  public static class InvokeAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleError(
        @Advice.FieldValue("requestContext") ClientRequest context,
        @Advice.Thrown Throwable throwable) {
      if (throwable != null) {
        Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          tracer().endExceptionally((Span) prop, throwable);
        }
      }
    }
  }

  public static class SubmitAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void handleError(
        @Advice.FieldValue("requestContext") ClientRequest context,
        @Advice.Return(readOnly = false) Future<?> future) {
      if (!(future instanceof WrappedFuture)) {
        future = new WrappedFuture<>(future, context);
      }
    }
  }

  public static class WrappedFuture<T> implements Future<T> {

    private final Future<T> wrapped;
    private final ClientRequest context;

    public WrappedFuture(Future<T> wrapped, ClientRequest context) {
      this.wrapped = wrapped;
      this.context = context;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
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
      } catch (ExecutionException e) {
        Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          tracer().endExceptionally((Span) prop, e.getCause());
        }
        throw e;
      }
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      try {
        return wrapped.get(timeout, unit);
      } catch (ExecutionException e) {
        Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          tracer().endExceptionally((Span) prop, e.getCause());
        }
        throw e;
      }
    }
  }
}
