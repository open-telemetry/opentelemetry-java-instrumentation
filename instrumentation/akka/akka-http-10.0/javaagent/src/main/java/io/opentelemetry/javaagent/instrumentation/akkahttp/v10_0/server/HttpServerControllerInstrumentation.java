/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.ErrorInfo;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class HttpServerControllerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // The anonymous GraphStageLogic class number differs between Akka HTTP versions.
    return nameStartsWith("akka.http.impl.engine.server.HttpServerBluePrint$ControllerStage$$anon$")
        .and(extendsClass(named("akka.stream.stage.GraphStageLogic")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameEndsWith("finishWithIllegalRequestError")
            .and(takesArgument(0, named("akka.http.scaladsl.model.StatusCode")))
            .and(takesArgument(1, named("akka.http.scaladsl.model.ErrorInfo"))),
        getClass().getName() + "$IllegalRequestAdvice");
    transformer.applyAdviceToMethod(
        nameEndsWith("emitErrorResponse")
            .and(takesArgument(0, named("akka.http.scaladsl.model.HttpResponse"))),
        getClass().getName() + "$ErrorResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class IllegalRequestAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ErrorInfo onEnter(@Advice.Argument(1) ErrorInfo info) {
      return AkkaHttpParsingErrorSingletons.enter(info);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable ErrorInfo previous) {
      AkkaHttpParsingErrorSingletons.exit(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class ErrorResponseAdvice {

    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static HttpResponse onEnter(@Advice.Argument(0) HttpResponse response) {
      return AkkaHttpParsingErrorSingletons.traceResponse(response);
    }
  }
}
