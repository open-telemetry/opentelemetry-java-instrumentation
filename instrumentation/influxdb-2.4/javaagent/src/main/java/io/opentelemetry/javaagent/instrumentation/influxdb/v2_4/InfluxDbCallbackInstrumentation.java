/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.util.VirtualField;
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
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Callback<?> callback) {
      InfluxDbQueryState state = currentContext().get(InfluxDbQueryState.KEY);
      if (state != null) {
        VirtualField.find(Callback.class, InfluxDbQueryState.class).set(callback, state);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Callback<?> callback, @Advice.Argument(0) Call<?> call) {
      InfluxDbQueryState state =
          VirtualField.find(Callback.class, InfluxDbQueryState.class).get(callback);
      if (state != null) {
        state.setCall(call);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Callback<?> callback,
        @Advice.Argument(0) Call<?> call,
        @Advice.Argument(1) Response<?> response,
        @Advice.Thrown @Nullable Throwable error) {
      InfluxDbQueryState state =
          VirtualField.find(Callback.class, InfluxDbQueryState.class).get(callback);
      if (state != null) {
        VirtualField.find(Callback.class, InfluxDbQueryState.class).set(callback, null);
        // onResponse owns the entire chunk processor, including EOF, cancellation and callbacks.
        state.endResponse(response, error, call.isCanceled());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class FailureAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Callback<?> callback,
        @Advice.Argument(0) Call<?> call,
        @Advice.Argument(1) Throwable error) {
      InfluxDbQueryState state =
          VirtualField.find(Callback.class, InfluxDbQueryState.class).get(callback);
      if (state != null) {
        VirtualField.find(Callback.class, InfluxDbQueryState.class).set(callback, null);
        state.end(call.isCanceled() ? null : error);
      }
    }
  }
}
