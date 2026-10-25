/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import akka.http.scaladsl.model.ErrorInfo;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ParsingErrorHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("akka.http.ParsingErrorHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("akka.http.ParsingErrorHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handle")
            .and(not(isAbstract()))
            .and(not(isBridge()))
            .and(takesArguments(4))
            .and(takesArgument(0, named("akka.http.scaladsl.model.StatusCode")))
            .and(takesArgument(1, named("akka.http.scaladsl.model.ErrorInfo")))
            .and(takesArgument(2, named("akka.event.LoggingAdapter")))
            .and(takesArgument(3, named("akka.http.scaladsl.settings.ServerSettings"))),
        getClass().getName() + "$HandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AkkaHttpParsingErrorSingletons.AdviceScope onEnter(
        @Advice.Argument(1) ErrorInfo info) {
      return AkkaHttpParsingErrorSingletons.startSpan(info);
    }

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static Object onExit(
        @Advice.Return Object response,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AkkaHttpParsingErrorSingletons.AdviceScope adviceScope) {
      if (adviceScope == null) {
        return response;
      }
      return adviceScope.end(response, throwable);
    }
  }
}
