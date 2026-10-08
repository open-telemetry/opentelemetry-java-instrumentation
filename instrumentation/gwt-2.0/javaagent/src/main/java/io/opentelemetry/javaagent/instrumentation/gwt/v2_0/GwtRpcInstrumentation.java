/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt.v2_0;

import static io.opentelemetry.javaagent.instrumentation.gwt.v2_0.GwtSingletons.RPC_FAILURE_KEY;
import static io.opentelemetry.javaagent.instrumentation.gwt.v2_0.GwtSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class GwtRpcInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.google.gwt.user.server.rpc.RPC");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invokeAndEncodeResponse")
            .and(takesArguments(5))
            .and(takesArgument(0, Object.class))
            .and(takesArgument(1, Method.class))
            .and(takesArgument(2, Object[].class))
            .and(takesArgument(3, named("com.google.gwt.user.server.rpc.SerializationPolicy")))
            .and(takesArgument(4, int.class)),
        getClass().getName() + "$InvokeAndEncodeResponseAdvice");

    // encodeResponseForFailure is called by invokeAndEncodeResponse in case of failure
    transformer.applyAdviceToMethod(
        named("encodeResponseForFailure")
            .and(takesArguments(4))
            .and(takesArgument(0, Method.class))
            .and(takesArgument(1, Throwable.class))
            .and(takesArgument(2, named("com.google.gwt.user.server.rpc.SerializationPolicy")))
            .and(takesArgument(3, int.class)),
        getClass().getName() + "$EncodeResponseForFailureAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAndEncodeResponseAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;
      private final AtomicReference<Throwable> failure;

      private AdviceScope(Context context, Scope scope, AtomicReference<Throwable> failure) {
        this.context = context;
        this.scope = scope;
        this.failure = failure;
      }

      @Nullable
      public static AdviceScope start(Method method) {
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, method)) {
          return null;
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Context context =
            instrumenter().start(parentContext, method).with(RPC_FAILURE_KEY, failure);
        return new AdviceScope(context, context.makeCurrent(), failure);
      }

      public void end(Method method, @Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, method, null, throwable != null ? throwable : failure.get());
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.Argument(1) Method method) {
      return AdviceScope.start(method);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(1) Method method,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(method, throwable);
      }
    }
  }

  public static class EncodeResponseForFailureAdvice {

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(1) @Nullable Throwable throwable) {
      if (throwable == null) {
        return;
      }
      AtomicReference<Throwable> failure =
          Java8BytecodeBridge.currentContext().get(RPC_FAILURE_KEY);
      if (failure == null) {
        // not inside rpc invocation
        return;
      }
      failure.set(throwable);
    }
  }
}
