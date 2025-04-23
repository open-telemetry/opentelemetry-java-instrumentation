/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.activejhttp.ActivejHttpServerSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ActivejAsyncServletInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("io.activej.http.AsyncServlet")).and(not(isInterface()));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.activej.http.AsyncServlet");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("serve"))
            .and(takesArguments(1).and(takesArgument(0, named("io.activej.http.HttpRequest")))),
        this.getClass().getName() + "$ServeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ServeAdvice {

    public static class AdviceLocals {
      public final HttpRequest httpRequest;
      public final Context context;
      public final Scope scope;

      public AdviceLocals(Context context, HttpRequest httpRequest) {
        this.context = context;
        this.scope = context.makeCurrent();
        this.httpRequest = httpRequest;
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceLocals methodEnter(
        @Advice.This AsyncServlet asyncServlet, @Advice.Argument(0) HttpRequest request) {

      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      return new AdviceLocals(instrumenter().start(parentContext, request), request);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Promise<HttpResponse> methodExit(
        @Advice.This AsyncServlet asyncServlet,
        @Advice.Return Promise<HttpResponse> responsePromise,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceLocals locals) {

      if (locals == null || locals.scope == null) {
        return responsePromise;
      }
      locals.scope.close();

      Promise<HttpResponse> returnValue = responsePromise;
      if (throwable != null) {
        instrumenter().end(locals.context, locals.httpRequest, null, throwable);
      } else {
        returnValue = PromiseWrapper.wrap(responsePromise, locals.httpRequest, locals.context);
      }
      return returnValue;
    }
  }
}
