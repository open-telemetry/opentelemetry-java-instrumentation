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
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.javadsl.model.HttpResponse;

/**
 * A request that fails to parse, for example because of an illegal request-target, is answered by
 * {@code HttpServerBluePrint$ControllerStage}, which sits below the stage that the server
 * instrumentation wraps. The rejected request never becomes an {@code HttpRequest} and never
 * reaches the user handler, so the only hook that sees it is the parsing error handler.
 */
class ParsingErrorHandlerInstrumentation implements TypeInstrumentation {

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
    // a scala implementation returns the more specific scaladsl HttpResponse and gets a bridge
    // method for the signature declared on ParsingErrorHandler; skipping the bridge leaves exactly
    // one match per implementation, whichever of the two return types it declares
    transformer.applyAdviceToMethod(
        named("handle").and(not(isAbstract())).and(not(isBridge())).and(takesArguments(4)),
        getClass().getName() + "$HandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter() {
      Context context = PekkoHttpParsingErrorSingletons.startSpan();
      if (context == null) {
        return null;
      }
      // the array carries the context and the scope from the enter advice to the exit advice
      return new Object[] {context, context.makeCurrent()};
    }

    // the return value is written back because the response customizer may have added headers, and
    // the typing is dynamic because a scala implementation declares the more specific scaladsl
    // HttpResponse, which the javadsl one that is returned here is assignable to at runtime
    @Advice.AssignReturned.ToReturned(typing = Assigner.Typing.DYNAMIC)
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static Object onExit(
        @Advice.Return Object response,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enter) {
      if (enter == null) {
        return response;
      }
      ((Scope) enter[1]).close();
      return PekkoHttpParsingErrorSingletons.endSpan(
          (Context) enter[0], (HttpResponse) response, throwable);
    }
  }
}
