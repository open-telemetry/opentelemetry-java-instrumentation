/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0_0.HbaseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hbase.common.CallMethodHelper;
import java.io.IOException;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public final class IpcCallInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hadoop.hbase.ipc.Call");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("callComplete")),
        IpcCallInstrumentation.class.getName() + "$CallCompleteAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(named("setTimeout")),
        IpcCallInstrumentation.class.getName() + "$CallTimeoutAdvice");
  }

  public static class CallCompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object call, @Advice.FieldValue(value = "error") IOException error) {
      CallMethodHelper.handleOnEnter(error, call, instrumenter());
    }
  }

  public static class CallTimeoutAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object call, @Advice.FieldValue(value = "error") IOException error) {
      CallMethodHelper.handleOnEnter(error, call, instrumenter());
    }
  }
}
