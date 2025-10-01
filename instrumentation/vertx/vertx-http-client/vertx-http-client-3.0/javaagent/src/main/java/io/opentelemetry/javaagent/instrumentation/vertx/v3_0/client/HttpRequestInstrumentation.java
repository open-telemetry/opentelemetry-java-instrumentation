/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client.VertxClientSingletons.CONTEXTS;
import static io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client.VertxClientSingletons.REQUEST_INFO;
import static io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client.VertxClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.client.Contexts;
import io.opentelemetry.javaagent.instrumentation.vertx.client.ExceptionHandlerWrapper;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
        isMethod().and(named("handleResponse")),
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

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope startAndAttachContext(HttpClientRequest request) {
        VertxRequestInfo requestInfo = REQUEST_INFO.get(request);
        if (requestInfo == null) {
          return null;
        }

        Context parentContext = Context.current();
        if (parentContext == null || parentContext == Context.root()) {
          io.vertx.core.Context vertxContext = Vertx.currentContext();
//          System.out.println("[VHCV3-1] Vertx Context: " + vertxContext);
          if (vertxContext != null && (vertxContext.get("otel.context")!=null&&vertxContext.get("otel.context")!=Context.root())) {
            Context storedOtelContext =
//                null;
                vertxContext.get("otel.context");
//            System.out.println(
//                "[VHCV3-2] Retrieved stored OTel context: " + storedOtelContext);
            parentContext = storedOtelContext;
          }
        }
        else {
//          System.out.println("[VHCV3-3] Parent context is not null: " + parentContext);
        }
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        CONTEXTS.set(request, new Contexts(parentContext, context));
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable, HttpClientRequest request) {
        scope.close();
        if (throwable != null) {
          instrumenter().end(context, request, null, throwable);
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope attachContext(@Advice.This HttpClientRequest request) {
      return AdviceScope.startAndAttachContext(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endScope(
        @Advice.This HttpClientRequest request,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, request);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class HandleExceptionAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope handleException(
        @Advice.This HttpClientRequest request, @Advice.Argument(0) Throwable t) {

      Contexts contexts = CONTEXTS.get(request);
      if (contexts == null) {
        return null;
      }
      instrumenter().end(contexts.context, request, null, t);

      // Scoping all potential callbacks etc to the parent context
      return contexts.parentContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleResponseExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class HandleResponseAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope handleResponseEnter(
        @Advice.This HttpClientRequest request, @Advice.Argument(0) HttpClientResponse response) {

      Contexts contexts = CONTEXTS.get(request);
      if (contexts == null) {
        return null;
      }
      instrumenter().end(contexts.context, request, response, null);

      // Scoping all potential callbacks etc to the parent context
      return contexts.parentContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleResponseExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class MountContextAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope mountContext(@Advice.This HttpClientRequest request) {
      Contexts contexts = CONTEXTS.get(request);
      if (contexts == null) {
        return null;
      }
      return contexts.context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void unmountContext(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExceptionHandlerAdvice {

    @Nullable
    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Handler<Throwable> wrapExceptionHandler(
        @Advice.This HttpClientRequest request,
        @Advice.Argument(0) @Nullable Handler<Throwable> handler) {
      if (handler == null) {
        return null;
      }
      return ExceptionHandlerWrapper.wrap(instrumenter(), request, CONTEXTS, handler);
    }
  }
}
