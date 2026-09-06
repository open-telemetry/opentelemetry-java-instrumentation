/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.io.IOException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.ipc.OpenTelemetryCallUtil;

class IpcCallInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hadoop.hbase.ipc.Call");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("callComplete"), getClass().getName() + "$CallCompleteAdvice");
    transformer.applyAdviceToMethod(
        named("setTimeout"), getClass().getName() + "$SetTimeoutAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallCompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object call,
        @Advice.FieldValue(value = "error") @Nullable IOException callError) {
      RequestAndContext requestAndContext =
          OpenTelemetryCallUtil.getAndClearRequestAndContext(call);
      if (requestAndContext == null) {
        return;
      }

      instrumenter()
          .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, callError);
    }
  }

  @SuppressWarnings("unused")
  public static class SetTimeoutAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Object call,
        @Advice.Argument(0) IOException timeoutError,
        @Advice.FieldValue(value = "error") @Nullable IOException callError) {
      RequestAndContext requestAndContext =
          OpenTelemetryCallUtil.getAndClearRequestAndContextIfError(call, callError, timeoutError);
      if (requestAndContext == null) {
        return;
      }

      instrumenter()
          .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, timeoutError);
    }
  }
}
