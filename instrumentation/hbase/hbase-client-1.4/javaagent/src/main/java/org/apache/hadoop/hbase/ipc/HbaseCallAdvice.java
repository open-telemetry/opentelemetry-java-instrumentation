/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseCallStateHelper;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4.HbaseSingletons;
import java.io.IOException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

// Advice parameters remain Object because indy advice executes outside the HBase runtime package.
@SuppressWarnings("unused")
public final class HbaseCallAdvice {

  public static class SendRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) Object call) {
      RequestAndContext requestAndContext = HbaseClientState.getRequestAndContext();
      HbaseCallStateHelper.set(call, requestAndContext);
    }
  }

  public static class CallCompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object call,
        @Advice.FieldValue(value = "error") @Nullable IOException callError) {
      RequestAndContext requestAndContext = HbaseCallStateHelper.getAndClear(call);
      if (requestAndContext == null) {
        return;
      }
      HbaseSingletons.instrumenter()
          .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, callError);
    }
  }

  public static class SetTimeoutAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Object call,
        @Advice.Argument(0) IOException timeoutError,
        @Advice.FieldValue(value = "error") @Nullable IOException callError) {
      RequestAndContext requestAndContext =
          HbaseCallStateHelper.getAndClearIfError(call, callError, timeoutError);
      if (requestAndContext == null) {
        return;
      }
      HbaseSingletons.instrumenter()
          .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, timeoutError);
    }
  }

  private HbaseCallAdvice() {}
}
