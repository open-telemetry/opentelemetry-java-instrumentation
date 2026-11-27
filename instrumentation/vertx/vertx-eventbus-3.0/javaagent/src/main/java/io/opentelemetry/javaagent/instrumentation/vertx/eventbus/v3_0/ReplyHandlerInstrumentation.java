/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Handles replies in the context they carry, see {@link ReplyContextHandler}, and the failure of a
 * request nobody replied to in the caller's context, see {@link FailureContextHandler}.
 */
class ReplyHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // added in 4.0
    return named("io.vertx.core.eventbus.impl.ReplyHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("doReceive").and(takesArguments(1)), getClass().getName() + "$DoReceiveAdvice");
    // doFail was added in 4.5, before that fail is called for every failure
    transformer.applyAdviceToMethod(
        namedOneOf("fail", "doFail")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.vertx.core.eventbus.ReplyException"))),
        getClass().getName() + "$FailAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    // the reply timeout is scheduled here, and then continues from the caller's context
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter() {
      return ProducerScope.callerContext().makeCurrent();
    }

    // exceptions thrown by a constructor can't be caught
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter Scope scope) {
      scope.close();
    }
  }

  @SuppressWarnings("unused")
  public static class DoReceiveAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.Argument(0) Message<?> reply) {
      if (NativeTracing.isEnabledForCurrentContext()) {
        return null;
      }
      return ReplyContextHandler.replyContext(reply).makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class FailAdvice {

    // no handlers fails the request while it is in progress, a timeout from the caller's context
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.Argument(0) ReplyException failure) {
      if (!FailureContextHandler.isUnanswered(failure)) {
        return null;
      }
      return ProducerScope.callerContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
