/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.CALLBACK_QUERY_STATE;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import retrofit2.Callback;

class InfluxDbExecutorCallbackInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "retrofit2.ExecutorCallAdapterFactory$ExecutorCallbackCall$1",
        "retrofit2.DefaultCallAdapterFactory$ExecutorCallbackCall$1");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, named("retrofit2.Callback"))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("onResponse")
            .and(takesArgument(1, named("retrofit2.Response")))
            .or(named("onFailure").and(takesArgument(1, Throwable.class))),
        getClass().getName() + "$CallbackAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Callback<?> executorCallback, @Advice.Argument(1) Callback<?> callback) {
      InfluxDbQueryState state = CALLBACK_QUERY_STATE.get(callback);
      if (state != null) {
        CALLBACK_QUERY_STATE.set(executorCallback, state);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CallbackAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Callback<?> executorCallback,
        @Advice.FieldValue("val$callback") Callback<?> callback,
        @Advice.Thrown @Nullable Throwable error) {
      InfluxDbQueryState state = CALLBACK_QUERY_STATE.get(executorCallback);
      if (state != null) {
        CALLBACK_QUERY_STATE.set(executorCallback, null);
        if (error != null) {
          CALLBACK_QUERY_STATE.set(callback, null);
          state.end(error);
        }
      }
    }
  }
}
