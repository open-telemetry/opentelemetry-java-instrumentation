/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

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
        namedOneOf("callComplete", "setTimeout"), getClass().getName() + "$CallAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object call,
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(value = 0, optional = true) @Nullable IOException timeoutError,
        @Advice.FieldValue(value = "error") @Nullable IOException callError) {
      IOException error = methodName.equals("setTimeout") ? timeoutError : callError;
      RequestAndContext requestAndContext =
          OpenTelemetryCallUtil.getAndClearRequestAndContext(call);
      if (requestAndContext == null) {
        return;
      }

      instrumenter()
          .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, error);
    }
  }
}
