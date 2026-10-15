/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.IllegalRequestContext;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;

/**
 * Pekko HTTP 2.0 added a parsing error handler overload that also carries what was read of the
 * rejected request, and answers rejections through it. Its default implementation delegates to the
 * overload that {@link ParsingErrorHandlerInstrumentation} instruments, which then finds the
 * rejection already traced, see {@link PekkoHttpParsingErrorSingletons#startSpan(ErrorInfo)}. A
 * handler that overrides only this overload never reaches the other one, so it has to be
 * instrumented on its own rather than only consulted for the request.
 */
class ParsingErrorHandlerRequestContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.pekko.http.ParsingErrorHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.apache.pekko.http.ParsingErrorHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // see ParsingErrorHandlerInstrumentation for why bridges are skipped and why every parameter
    // type is matched
    transformer.applyAdviceToMethod(
        named("handle")
            .and(not(isAbstract()))
            .and(not(isBridge()))
            .and(takesArguments(5))
            .and(takesArgument(0, named("org.apache.pekko.http.scaladsl.model.StatusCode")))
            .and(takesArgument(1, named("org.apache.pekko.http.scaladsl.model.ErrorInfo")))
            .and(takesArgument(2, named("org.apache.pekko.event.LoggingAdapter")))
            .and(takesArgument(3, named("org.apache.pekko.http.scaladsl.settings.ServerSettings")))
            .and(takesArgument(4, named("org.apache.pekko.http.IllegalRequestContext"))),
        getClass().getName() + "$HandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(1) ErrorInfo info, @Advice.Argument(4) IllegalRequestContext context) {
      return PekkoHttpIllegalRequestContextUtil.startSpan(info, context);
    }

    // see ParsingErrorHandlerInstrumentation for why the return value is written back
    @Advice.AssignReturned.ToReturned(typing = Assigner.Typing.DYNAMIC)
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static Object onExit(
        @Advice.Return Object response,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enter) {
      if (enter == null) {
        return response;
      }
      return PekkoHttpParsingErrorSingletons.endSpan(enter, (HttpResponse) response, throwable);
    }
  }
}
