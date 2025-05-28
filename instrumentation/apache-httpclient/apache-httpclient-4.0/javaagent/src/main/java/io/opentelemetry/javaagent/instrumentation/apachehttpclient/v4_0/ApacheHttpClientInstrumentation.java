/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.instrumenter;
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
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.http.client.HttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.http.client.HttpClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // There are 8 execute(...) methods.  Depending on the version, they may or may not delegate
    // to each other. Thus, all methods need to be instrumented.  Because of argument position and
    // type, some methods can share the same advice class.  The call depth tracking ensures only 1
    // span is created

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest"))),
        this.getClass().getName() + "$UriRequestAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.protocol.HttpContext"))),
        this.getClass().getName() + "$UriRequestAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.client.ResponseHandler"))),
        this.getClass().getName() + "$UriRequestWithHandlerAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.client.ResponseHandler")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        this.getClass().getName() + "$UriRequestWithHandlerAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest"))),
        this.getClass().getName() + "$RequestAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        this.getClass().getName() + "$RequestAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.client.ResponseHandler"))),
        this.getClass().getName() + "$RequestWithHandlerAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.client.ResponseHandler")))
            .and(takesArgument(3, named("org.apache.http.protocol.HttpContext"))),
        this.getClass().getName() + "$RequestWithHandlerAdvice");
  }

  public static class AdviceScope {
    private final ApacheHttpClientRequest otelRequest;
    private final Context parentContext;
    private final Context context;
    private final Scope scope;

    private AdviceScope(
        ApacheHttpClientRequest otelRequest, Context parentContext, Context context, Scope scope) {
      this.otelRequest = otelRequest;
      this.parentContext = parentContext;
      this.context = context;
      this.scope = scope;
    }

    @Nullable
    public static AdviceScope start(ApacheHttpClientRequest otelRequest) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, otelRequest);
      return new AdviceScope(otelRequest, parentContext, context, context.makeCurrent());
    }

    public <T> ResponseHandler<T> wrapHandler(ResponseHandler<T> handler) {
      return new WrappingStatusSettingResponseHandler<>(
          context, parentContext, otelRequest, handler);
    }

    public void end(@Nullable Object result, @Nullable Throwable throwable) {
      scope.close();
      if (throwable != null) {
        instrumenter().end(context, otelRequest, null, throwable);
      } else if (result instanceof HttpResponse) {
        instrumenter().end(context, otelRequest, (HttpResponse) result, null);
      }
      // ended in WrappingStatusSettingResponseHandler
    }
  }

  @SuppressWarnings("unused")
  public static class UriRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(@Advice.Argument(0) HttpUriRequest request) {
      return AdviceScope.start(new ApacheHttpClientRequest(request));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) HttpUriRequest request,
        @Advice.Return @Nullable Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      if (adviceScope != null) {
        adviceScope.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class UriRequestWithHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) HttpUriRequest request,
        @Advice.Argument(1) ResponseHandler<?> handler) {

      AdviceScope adviceScope = AdviceScope.start(new ApacheHttpClientRequest(request));
      // Wrap the handler so we capture the status code
      return new Object[] {
        adviceScope, adviceScope == null ? handler : adviceScope.wrapHandler(handler)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(
        @Advice.Argument(0) HttpHost host, @Advice.Argument(1) HttpRequest request) {
      return AdviceScope.start(new ApacheHttpClientRequest(host, request));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter AdviceScope adviceScope) {

      if (adviceScope != null) {
        adviceScope.end(result, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) HttpRequest request,
        @Advice.Argument(2) ResponseHandler<?> handler) {

      AdviceScope adviceScope = AdviceScope.start(new ApacheHttpClientRequest(host, request));
      return new Object[] {
        adviceScope,
        // Wrap the handler so we capture the status code
        adviceScope == null ? handler : adviceScope.wrapHandler(handler)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter Object[] enterResult) {

      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(result, throwable);
      }
    }
  }
}
