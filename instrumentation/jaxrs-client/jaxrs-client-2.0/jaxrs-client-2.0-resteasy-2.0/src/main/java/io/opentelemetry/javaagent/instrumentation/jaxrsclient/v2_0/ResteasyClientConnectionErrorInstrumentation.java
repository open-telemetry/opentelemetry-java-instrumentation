/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.ResteasyClientTracer.TRACER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

/**
 * Unlike other supported JAX-RS Client implementations, Resteasy's one is very simple and passes
 * all requests through single point. Both sync ADN async! This allows for easy instrumentation and
 * proper scope handling.
 *
 * <p>This specific instrumentation will not conflict with {@link JaxRsClientInstrumentation},
 * because {@link JaxRsClientTracer} used by the latter checks against double client spans.
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
      packageName + ".ResteasyClientTracer", packageName + ".ResteasyInjectAdapter",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        isMethod().and(isPublic()).and(named("invoke")).and(takesArguments(0)),
        ResteasyClientConnectionErrorInstrumentation.class.getName() + "$InvokeAdvice");

    return transformers;
  }

  public static class InvokeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ClientInvocation invocation,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      span = TRACER.startSpan(invocation);
      scope = TRACER.startScope(span, invocation);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Response response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {

      scope.close();

      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span, response);
      }
    }
  }
}
