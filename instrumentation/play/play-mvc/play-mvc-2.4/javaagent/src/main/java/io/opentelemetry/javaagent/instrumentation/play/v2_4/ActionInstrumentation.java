/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.play.v2_4.Play24Singletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.play.v2_4.Play24Singletons.updateSpan;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

public class ActionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("play.api.mvc.Action");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("play.api.mvc.Action"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("apply")
            .and(takesArgument(0, named("play.api.mvc.Request")))
            .and(returns(named("scala.concurrent.Future"))),
        this.getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      public AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Context parentContext) {
        if (!instrumenter().shouldStart(parentContext, null)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, null);
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(
          @Nullable Throwable throwable,
          Future<Result> responseFuture,
          Action<?> thisAction,
          Request<?> req) {
        scope.close();
        updateSpan(context, req);

        if (throwable == null) {
          // span finished in RequestCompleteCallback
          responseFuture.onComplete(
              new RequestCompleteCallback(context), thisAction.executionContext());
        } else {
          instrumenter().end(context, null, null, throwable);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) Request<?> req) {
      return AdviceScope.start(currentContext());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopTraceOnResponse(
        @Advice.This Action<?> thisAction,
        @Advice.Thrown Throwable throwable,
        @Advice.Argument(0) Request<?> req,
        @Advice.Return Future<Result> responseFuture,
        @Advice.Enter @Nullable AdviceScope actionScope) {
      if (actionScope == null) {
        return;
      }

      actionScope.end(throwable, responseFuture, thisAction, req);
    }
  }
}
