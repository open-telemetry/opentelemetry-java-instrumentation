/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.CALLBACK_QUERY_STATE;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

class InfluxDbCallbackInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("org.influxdb.impl.InfluxDBImpl$")
        .and(hasSuperType(named("retrofit2.Callback")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("onResponse")
            .and(takesArguments(2))
            .and(takesArgument(1, named("retrofit2.Response"))),
        getClass().getName() + "$ResponseAdvice");
    transformer.applyAdviceToMethod(
        named("onFailure").and(takesArguments(2)).and(takesArgument(1, Throwable.class)),
        getClass().getName() + "$FailureAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Callback<?> callback) {
      InfluxDbQueryState state = Context.current().get(InfluxDbQueryState.KEY);
      if (state != null && state.associateCallback()) {
        CALLBACK_QUERY_STATE.set(callback, state);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope onEnter(
        @Advice.This Callback<?> callback, @Advice.Argument(0) Call<?> call) {
      InfluxDbQueryState state = CALLBACK_QUERY_STATE.get(callback);
      if (state != null) {
        state.setCall(call);
        return state.getContext().makeCurrent();
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Callback<?> callback,
        @Advice.Argument(1) Response<?> response,
        @Advice.Enter @Nullable Scope scope,
        @Advice.Thrown @Nullable Throwable error) {
      if (scope != null) {
        scope.close();
      }
      InfluxDbQueryState state = CALLBACK_QUERY_STATE.get(callback);
      if (state != null) {
        CALLBACK_QUERY_STATE.set(callback, null);
        state.endResponse(response, error);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class FailureAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This Callback<?> callback,
        @Advice.Argument(0) Call<?> call,
        @Advice.Argument(1) Throwable error) {
      InfluxDbQueryState state = CALLBACK_QUERY_STATE.get(callback);
      if (state != null) {
        state.setCall(call);
        state.onFailure(error);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Callback<?> callback) {
      InfluxDbQueryState state = CALLBACK_QUERY_STATE.get(callback);
      if (state != null) {
        CALLBACK_QUERY_STATE.set(callback, null);
        state.end(null);
      }
    }
  }
}
