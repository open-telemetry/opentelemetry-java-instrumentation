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
package io.opentelemetry.auto.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.auto.instrumentation.jaxrs.v2_0.JaxRsAnnotationsDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jaxrs.v2_0.JaxRsAnnotationsDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.hasSuperMethod;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
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
  public Map<String, String> contextStore() {
    return singletonMap("javax.ws.rs.container.AsyncResponse", Span.class.getName());
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.ws.rs.Path");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        isAnnotatedWith(named("javax.ws.rs.Path"))
            .<TypeDescription>or(declaresMethod(isAnnotatedWith(named("javax.ws.rs.Path")))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable",
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(
                hasSuperMethod(
                    isAnnotatedWith(
                        named("javax.ws.rs.Path")
                            .or(named("javax.ws.rs.DELETE"))
                            .or(named("javax.ws.rs.GET"))
                            .or(named("javax.ws.rs.HEAD"))
                            .or(named("javax.ws.rs.OPTIONS"))
                            .or(named("javax.ws.rs.POST"))
                            .or(named("javax.ws.rs.PUT"))))),
        JaxRsAnnotationsInstrumentation.class.getName() + "$JaxRsAnnotationsAdvice");
  }

  public static class JaxRsAnnotationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope nameSpan(
        @Advice.This final Object target,
        @Advice.Origin final Method method,
        @Advice.AllArguments final Object[] args,
        @Advice.Local("asyncResponse") AsyncResponse asyncResponse) {
      ContextStore<AsyncResponse, Span> contextStore = null;
      for (final Object arg : args) {
        if (arg instanceof AsyncResponse) {
          asyncResponse = (AsyncResponse) arg;
          contextStore = InstrumentationContext.get(AsyncResponse.class, Span.class);
          if (contextStore.get(asyncResponse) != null) {
            /**
             * We are probably in a recursive call and don't want to start a new span because it
             * would replace the existing span in the asyncResponse and cause it to never finish. We
             * could work around this by using a list instead, but we likely don't want the extra
             * span anyway.
             */
            return null;
          }
          break;
        }
      }

      if (CallDepthThreadLocalMap.incrementCallDepth(Path.class) > 0) {
        return null;
      }

      // Rename the parent span according to the path represented by these annotations.
      final Span parent = TRACER.getCurrentSpan();

      final Span span = TRACER.spanBuilder(JAX_ENDPOINT_OPERATION_NAME).startSpan();
      DECORATE.onJaxRsSpan(span, parent, target.getClass(), method);
      DECORATE.afterStart(span);

      if (contextStore != null && asyncResponse != null) {
        contextStore.put(asyncResponse, span);
      }

      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Local("asyncResponse") final AsyncResponse asyncResponse) {
      if (spanWithScope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(Path.class);

      final Span span = spanWithScope.getSpan();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        spanWithScope.closeScope();
        return;
      }

      if (asyncResponse != null && !asyncResponse.isSuspended()) {
        // Clear span from the asyncResponse. Logically this should never happen. Added to be safe.
        InstrumentationContext.get(AsyncResponse.class, Span.class).put(asyncResponse, null);
      }
      if (asyncResponse == null || !asyncResponse.isSuspended()) {
        DECORATE.beforeFinish(span);
        span.end();
      }
      spanWithScope.closeScope();
      // else span finished by AsyncResponseAdvice
    }
  }
}
