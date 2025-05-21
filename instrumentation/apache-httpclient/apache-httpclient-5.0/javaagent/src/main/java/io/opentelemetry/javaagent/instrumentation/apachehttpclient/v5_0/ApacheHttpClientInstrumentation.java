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

  public static class AdviceLocals {
    public final ClassicHttpRequest request;
    public final Context parentContext;
    public final Context context;
    public final Scope scope;

    private AdviceLocals(
        ClassicHttpRequest request, Context parentContext, Context context, Scope scope) {
      this.request = request;
      this.context = context;
      this.parentContext = parentContext;
      this.scope = scope;
    }

    @Nullable
    public static AdviceLocals start(ClassicHttpRequest request) {
      Context parentContext = currentContext();

      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      return new AdviceLocals(request, parentContext, context, context.makeCurrent());
    }

    public void end(Object result, Throwable throwable) {
      scope.close();
      ApacheHttpClientHelper.doMethodExit(context, request, result, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceLocals methodEnter(@Advice.Argument(0) ClassicHttpRequest request) {
      return AdviceLocals.start(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceLocals locals) {

      if (locals != null) {
        locals.end(result, throwable);
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
      AdviceLocals locals = AdviceLocals.start(request);
      if (locals == null) {
        return new Object[] {null, handler};
      }

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler =
            new WrappingStatusSettingResponseHandler<>(
                locals.context, locals.parentContext, request, handler);
      }
      return new Object[] {locals, handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceLocals locals = (AdviceLocals) enterResult[0];
      if (locals != null) {
        locals.end(result, throwable);
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
      AdviceLocals locals = AdviceLocals.start(request);
      if (locals == null) {
        return new Object[] {null, handler};
      }

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler =
            new WrappingStatusSettingResponseHandler<>(
                locals.context, locals.parentContext, request, handler);
      }
      return new Object[] {locals, handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceLocals locals = (AdviceLocals) enterResult[0];
      if (locals != null) {
        locals.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithHostAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceLocals methodEnter(
        @Advice.Argument(0) HttpHost host, @Advice.Argument(1) ClassicHttpRequest request) {

      return AdviceLocals.start(new RequestWithHost(host, request));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceLocals locals) {

      if (locals != null) {
        locals.end(result, throwable);
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
      AdviceLocals locals = AdviceLocals.start(new RequestWithHost(host, request));

      if (locals == null) {
        return new Object[] {null, handler};
      }

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler =
            new WrappingStatusSettingResponseHandler<>(
                locals.context, locals.parentContext, locals.request, handler);
      }
      return new Object[] {locals, handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceLocals locals = (AdviceLocals) enterResult[0];
      if (locals != null) {
        locals.end(result, throwable);
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
      AdviceLocals locals = AdviceLocals.start(new RequestWithHost(host, request));

      if (locals == null) {
        return new Object[] {null, handler};
      }

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler =
            new WrappingStatusSettingResponseHandler<>(
                locals.context, locals.parentContext, locals.request, handler);
      }
      return new Object[] {locals, handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceLocals locals = (AdviceLocals) enterResult[0];
      if (locals != null) {
        locals.end(result, throwable);
      }
    }
  }
}
