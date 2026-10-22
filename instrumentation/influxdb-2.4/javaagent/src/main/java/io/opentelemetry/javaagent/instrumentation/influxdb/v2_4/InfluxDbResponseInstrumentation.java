/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.CALLBACK_QUERY_STATE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import retrofit2.Call;
import retrofit2.Callback;

class InfluxDbResponseInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("retrofit2.OkHttpCall$1");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onResponse").and(takesArgument(1, named("okhttp3.Response"))),
        getClass().getName() + "$ResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope onEnter(
        @Advice.FieldValue("val$callback") Callback<?> callback,
        @Advice.FieldValue("this$0") Call<?> call) {
      InfluxDbQueryState state = CALLBACK_QUERY_STATE.get(callback);
      if (state == null) {
        return null;
      }
      state.setCall(call);
      return state
          .getContext()
          .with(InfluxDbQueryState.RESPONSE_PROCESSING_STATE, state)
          .makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.FieldValue("val$callback") Callback<?> callback,
        @Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
        CALLBACK_QUERY_STATE.set(callback, null);
      }
    }
  }
}
