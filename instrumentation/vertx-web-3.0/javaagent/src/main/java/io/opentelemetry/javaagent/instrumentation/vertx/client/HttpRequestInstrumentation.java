/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import static io.opentelemetry.javaagent.instrumentation.vertx.client.VertxClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Two things happen in this instrumentation.
 *
 * <p>First, {@link EndRequestAdvice}, {@link HandleExceptionAdvice} and {@link
 * HandleResponseAdvice} deal with the common start span/end span functionality. As Vert.x is async
 * framework, calls to the instrumented methods may happen from different threads. Thus, correct
 * context is stored in {@code HttpClientRequest} itself.
 *
 * <p>Second, when HttpClientRequest calls any method that actually performs write on the underlying
 * Netty channel, {@link MountContextAdvice} scopes that method call into the context captured on
 * the first step. This ensures proper context transfer between the client who actually initiated
 * the http call and the Netty Channel that will perform that operation. The main result of this
 * transfer is a suppression of Netty CLIENT span.
 */
public class HttpRequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.core.http.HttpClientRequest");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.core.http.HttpClientRequest"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        isMethod().and(nameStartsWith("end").or(named("sendHead"))),
        HttpRequestInstrumentation.class.getName() + "$EndRequestAdvice");

    transformers.put(
        isMethod().and(named("handleException")),
        HttpRequestInstrumentation.class.getName() + "$HandleExceptionAdvice");

    transformers.put(
        isMethod().and(named("handleResponse")),
        HttpRequestInstrumentation.class.getName() + "$HandleResponseAdvice");

    transformers.put(
        isMethod().and(isPrivate()).and(nameStartsWith("write").or(nameStartsWith("connected"))),
        HttpRequestInstrumentation.class.getName() + "$MountContextAdvice");
    return transformers;
  }

  public static class EndRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void attachContext(
        @Advice.This HttpClientRequest request, @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Java8BytecodeBridge.currentContext();

      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      Context context = tracer().startSpan(parentContext, request, request);
      Contexts contexts = new Contexts(parentContext, context);
      InstrumentationContext.get(HttpClientRequest.class, Contexts.class).put(request, contexts);

      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endScope(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  public static class HandleExceptionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void handleException(
        @Advice.This HttpClientRequest request,
        @Advice.Argument(0) Throwable t,
        @Advice.Local("otelScope") Scope scope) {
      Contexts contexts =
          InstrumentationContext.get(HttpClientRequest.class, Contexts.class).get(request);

      if (contexts == null) {
        return;
      }

      tracer().endExceptionally(contexts.context, t);

      // Scoping all potential callbacks etc to the parent context
      scope = contexts.parentContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleResponseExit(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  public static class HandleResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void handleResponseEnter(
        @Advice.This HttpClientRequest request,
        @Advice.Argument(0) HttpClientResponse response,
        @Advice.Local("otelScope") Scope scope) {
      Contexts contexts =
          InstrumentationContext.get(HttpClientRequest.class, Contexts.class).get(request);

      if (contexts == null) {
        return;
      }

      tracer().end(contexts.context, response);

      // Scoping all potential callbacks etc to the parent context
      scope = contexts.parentContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleResponseExit(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  public static class MountContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void mountContext(
        @Advice.This HttpClientRequest request, @Advice.Local("otelScope") Scope scope) {
      Contexts contexts =
          InstrumentationContext.get(HttpClientRequest.class, Contexts.class).get(request);
      if (contexts == null) {
        return;
      }

      scope = contexts.context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void unmountContext(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
