/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client.VertxClientTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.vertx.client.Contexts;
import io.opentelemetry.javaagent.instrumentation.vertx.client.ExceptionHandlerWrapper;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import net.bytebuddy.asm.Advice;
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
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(nameStartsWith("end").or(named("sendHead"))),
        HttpRequestInstrumentation.class.getName() + "$EndRequestAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(named("handleException")),
        HttpRequestInstrumentation.class.getName() + "$HandleExceptionAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("handleResponse"))
            .and(takesArgument(1, named("io.vertx.core.http.HttpClientResponse"))),
        HttpRequestInstrumentation.class.getName() + "$HandleResponseAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(isPrivate()).and(nameStartsWith("write").or(nameStartsWith("connected"))),
        HttpRequestInstrumentation.class.getName() + "$MountContextAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("exceptionHandler"))
            .and(takesArgument(0, named("io.vertx.core.Handler"))),
        HttpRequestInstrumentation.class.getName() + "$ExceptionHandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class EndRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void attachContext(
        @Advice.This HttpClientRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Java8BytecodeBridge.currentContext();

      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      context = tracer().startSpan(parentContext, request, request);
      Contexts contexts = new Contexts(parentContext, context);
      InstrumentationContext.get(HttpClientRequest.class, Contexts.class).put(request, contexts);

      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endScope(
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope != null) {
        scope.close();
      }
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
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

  @SuppressWarnings("unused")
  public static class HandleResponseAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void handleResponseEnter(
        @Advice.This HttpClientRequest request,
        @Advice.Argument(1) HttpClientResponse response,
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

  @SuppressWarnings("unused")
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

  @SuppressWarnings("unused")
  public static class ExceptionHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapExceptionHandler(
        @Advice.This HttpClientRequest request,
        @Advice.Argument(value = 0, readOnly = false) Handler<Throwable> handler) {
      if (handler != null) {
        ContextStore<HttpClientRequest, Contexts> contextStore =
            InstrumentationContext.get(HttpClientRequest.class, Contexts.class);
        handler = ExceptionHandlerWrapper.wrap(tracer(), request, contextStore, handler);
      }
    }
  }
}
