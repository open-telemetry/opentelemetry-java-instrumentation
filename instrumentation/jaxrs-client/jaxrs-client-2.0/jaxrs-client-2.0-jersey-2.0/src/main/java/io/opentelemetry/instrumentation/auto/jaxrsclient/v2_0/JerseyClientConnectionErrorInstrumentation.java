/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.jaxrsclient.v2_0;

import static io.opentelemetry.instrumentation.auto.jaxrsclient.v2_0.JaxRsClientTracer.TRACER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
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
import org.glassfish.jersey.client.ClientRequest;

/**
 * JAX-RS Client API doesn't define a good point where we can handle connection failures, so we must
 * handle these errors at the implementation level.
 */
@AutoService(Instrumenter.class)
public final class JerseyClientConnectionErrorInstrumentation extends Instrumenter.Default {

  public JerseyClientConnectionErrorInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.jersey.client.JerseyInvocation");
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
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        isMethod().and(isPublic()).and(named("invoke")),
        JerseyClientConnectionErrorInstrumentation.class.getName() + "$InvokeAdvice");

    transformers.put(
        isMethod().and(isPublic()).and(named("submit")).and(returns(Future.class)),
        JerseyClientConnectionErrorInstrumentation.class.getName() + "$SubmitAdvice");

    return transformers;
  }

  public static class InvokeAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleError(
        @Advice.FieldValue("requestContext") final ClientRequest context,
        @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          TRACER.endExceptionally((Span) prop, throwable);
        }
      }
    }
  }

  public static class SubmitAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void handleError(
        @Advice.FieldValue("requestContext") final ClientRequest context,
        @Advice.Return(readOnly = false) Future<?> future) {
      if (!(future instanceof WrappedFuture)) {
        future = new WrappedFuture<>(future, context);
      }
    }
  }

  public static class WrappedFuture<T> implements Future<T> {

    private final Future<T> wrapped;
    private final ClientRequest context;

    public WrappedFuture(final Future<T> wrapped, final ClientRequest context) {
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
        Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          TRACER.endExceptionally((Span) prop, e.getCause());
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
        Object prop = context.getProperty(ClientTracingFilter.SPAN_PROPERTY_NAME);
        if (prop instanceof Span) {
          TRACER.endExceptionally((Span) prop, e.getCause());
        }
        throw e;
      }
    }
  }
}
