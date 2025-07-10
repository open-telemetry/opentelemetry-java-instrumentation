/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

class ApacheHttpClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.hc.client5.http.classic.HttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.hc.client5.http.classic.HttpClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // There are 8 execute(...) methods. Depending on the version, they may or may not delegate
    // to each other. Thus, all methods need to be instrumented. Because of argument position and
    // type, some methods can share the same advice class. The call depth tracking ensures only 1
    // span is created
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.hc.core5.http.ClassicHttpRequest"))),
        this.getClass().getName() + "$RequestAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.hc.core5.http.ClassicHttpRequest")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.protocol.HttpContext"))),
        this.getClass().getName() + "$RequestAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest"))),
        this.getClass().getName() + "$RequestWithHostAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.protocol.HttpContext"))),
        this.getClass().getName() + "$RequestWithHostAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.hc.core5.http.ClassicHttpRequest")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.io.HttpClientResponseHandler"))),
        this.getClass().getName() + "$RequestWithHandlerAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.hc.core5.http.ClassicHttpRequest")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.protocol.HttpContext")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.io.HttpClientResponseHandler"))),
        this.getClass().getName() + "$RequestWithContextAndHandlerAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.io.HttpClientResponseHandler"))),
        this.getClass().getName() + "$RequestWithHostAndHandlerAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.hc.core5.http.io.HttpClientResponseHandler"))),
        this.getClass().getName() + "$RequestWithHostAndContextAndHandlerAdvice");
  }

  public static class AdviceScope {
    private final ClassicHttpRequest request;
    private final Context parentContext;
    private final Context context;
    private final Scope scope;

    private AdviceScope(
        ClassicHttpRequest request, Context parentContext, Context context, Scope scope) {
      this.request = request;
      this.context = context;
      this.parentContext = parentContext;
      this.scope = scope;
    }

    @Nullable
    public static AdviceScope start(ClassicHttpRequest request) {
      Context parentContext = currentContext();

      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(request, parentContext, context, context.makeCurrent());
    }

    public void end(@Nullable Object result, @Nullable Throwable throwable) {
      scope.close();
      if (throwable != null) {
        instrumenter().end(context, request, null, throwable);
      } else if (result instanceof HttpResponse) {
        instrumenter().end(context, request, (HttpResponse) result, null);
      }
      // ended in WrappingStatusSettingResponseHandler
    }

    public WrappingStatusSettingResponseHandler<?> wrapResponseHandler(
        HttpClientResponseHandler<?> handler) {
      return new WrappingStatusSettingResponseHandler<>(context, parentContext, request, handler);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(@Advice.Argument(0) ClassicHttpRequest request) {
      return AdviceScope.start(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope scope) {

      if (scope != null) {
        scope.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Argument(1) HttpClientResponseHandler<?> originalHandler) {

      HttpClientResponseHandler<?> handler = originalHandler;
      AdviceScope scope = AdviceScope.start(request);
      if (scope == null) {
        return new Object[] {null, handler};
      }

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler = scope.wrapResponseHandler(handler);
      }
      return new Object[] {scope, handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Return @Nullable Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceScope scope = (AdviceScope) enterResult[0];
      if (scope != null) {
        scope.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithContextAndHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Argument(2) HttpClientResponseHandler<?> originalHandler) {

      HttpClientResponseHandler<?> handler = originalHandler;
      AdviceScope scope = AdviceScope.start(request);
      if (scope == null) {
        return new Object[] {null, handler};
      }

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler = scope.wrapResponseHandler(handler);
      }
      return new Object[] {scope, handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Return @Nullable Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceScope scope = (AdviceScope) enterResult[0];
      if (scope != null) {
        scope.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithHostAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(
        @Advice.Argument(0) HttpHost host, @Advice.Argument(1) ClassicHttpRequest request) {

      return AdviceScope.start(new RequestWithHost(host, request));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope scope) {

      if (scope != null) {
        scope.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithHostAndHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) ClassicHttpRequest request,
        @Advice.Argument(2) HttpClientResponseHandler<?> originalHandler) {

      HttpClientResponseHandler<?> handler = originalHandler;
      AdviceScope scope = AdviceScope.start(new RequestWithHost(host, request));

      if (scope == null) {
        return new Object[] {null, handler};
      }

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler = scope.wrapResponseHandler(handler);
      }
      return new Object[] {scope, handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return @Nullable Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceScope scope = (AdviceScope) enterResult[0];
      if (scope != null) {
        scope.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithHostAndContextAndHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 3, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) ClassicHttpRequest request,
        @Advice.Argument(3) HttpClientResponseHandler<?> originalHandler) {

      HttpClientResponseHandler<?> handler = originalHandler;
      AdviceScope scope = AdviceScope.start(new RequestWithHost(host, request));

      if (scope == null) {
        return new Object[] {null, handler};
      }

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler = scope.wrapResponseHandler(handler);
      }
      return new Object[] {scope, handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceScope scope = (AdviceScope) enterResult[0];
      if (scope != null) {
        scope.end(result, throwable);
      }
    }
  }
}
