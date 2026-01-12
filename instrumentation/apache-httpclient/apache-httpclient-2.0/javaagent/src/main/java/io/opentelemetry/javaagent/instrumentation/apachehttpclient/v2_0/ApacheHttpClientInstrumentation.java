/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0.ApacheHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.httpclient.HttpMethod;

public class ApacheHttpClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.commons.httpclient.HttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.commons.httpclient.HttpClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("executeMethod"))
            .and(takesArguments(3))
            .and(takesArgument(1, named("org.apache.commons.httpclient.HttpMethod"))),
        this.getClass().getName() + "$ExecuteMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteMethodAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;
      private final HttpMethod httpMethod;

      private AdviceScope(Context context, Scope scope, HttpMethod httpMethod) {
        this.context = context;
        this.scope = scope;
        this.httpMethod = httpMethod;
      }

      @Nullable
      public static AdviceScope start(HttpMethod httpMethod) {
        Context parentContext = currentContext();
        if (!instrumenter().shouldStart(parentContext, httpMethod)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, httpMethod);
        return new AdviceScope(context, context.makeCurrent(), httpMethod);
      }

      public void end(Throwable throwable) {
        scope.close();
        instrumenter().end(context, httpMethod, httpMethod, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(@Advice.Argument(1) HttpMethod httpMethod) {
      return AdviceScope.start(httpMethod);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
