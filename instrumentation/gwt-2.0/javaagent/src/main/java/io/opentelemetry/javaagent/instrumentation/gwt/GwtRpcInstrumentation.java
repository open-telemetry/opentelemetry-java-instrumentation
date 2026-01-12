/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import static io.opentelemetry.javaagent.instrumentation.gwt.GwtSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GwtRpcInstrumentation implements TypeInstrumentation {

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
        this.getClass().getName() + "$InvokeAndEncodeResponseAdvice");

    // encodeResponseForFailure is called by invokeAndEncodeResponse in case of failure
    transformer.applyAdviceToMethod(
        named("encodeResponseForFailure")
            .and(takesArguments(4))
            .and(takesArgument(0, Method.class))
            .and(takesArgument(1, Throwable.class))
            .and(takesArgument(2, named("com.google.gwt.user.server.rpc.SerializationPolicy")))
            .and(takesArgument(3, int.class)),
        this.getClass().getName() + "$EncodeResponseForFailureAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAndEncodeResponseAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Method method) {
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, method)) {
          return null;
        }
        Context context =
            instrumenter().start(parentContext, method).with(GwtSingletons.RPC_CONTEXT_KEY, true);
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(Method method, @Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, method, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(1) Method method) {
      return AdviceScope.start(method);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(1) Method method,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(method, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class EncodeResponseForFailureAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(1) @Nullable Throwable throwable) {
      if (throwable == null) {
        return;
      }
      Context context = Java8BytecodeBridge.currentContext();
      if (context.get(GwtSingletons.RPC_CONTEXT_KEY) == null) {
        // not inside rpc invocation
        return;
      }
      Java8BytecodeBridge.spanFromContext(context).recordException(throwable);
    }
  }
}
