/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseCallStateHelper;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons;
import java.io.IOException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import org.apache.hbase.thirdparty.io.netty.channel.ChannelHandlerContext;

// Advice-only bridge into the package-private hbase Call class. Bytecode from these @Advice
// methods is inlined into the transformed target class at instrumentation time, so referencing
// the package-private Call type is safe even though the containing java file physically lives
// in this library package.
//
// This class is registered exclusively via TypeTransformer#applyAdviceToMethod and must not be
// injected as a helper class or otherwise referenced from live production code.
@SuppressWarnings("unused")
public final class HbaseCallAdvice {

  public static class SendRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) Object call) {
      RequestAndContext requestAndContext = HbaseClientState.getRequestAndContext();
      VirtualField.find(Call.class, RequestAndContext.class).set((Call) call, requestAndContext);
    }
  }

  public static class CallCompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object call,
        @Advice.FieldValue(value = "error") @Nullable IOException callError) {
      RequestAndContext requestAndContext =
          HbaseCallStateHelper.getAndClear(
              VirtualField.find(Call.class, RequestAndContext.class), (Call) call);
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
          HbaseCallStateHelper.getAndClearIfError(
              VirtualField.find(Call.class, RequestAndContext.class),
              (Call) call,
              callError,
              timeoutError);
      if (requestAndContext == null) {
        return;
      }
      HbaseSingletons.instrumenter()
          .end(requestAndContext.getContext(), requestAndContext.getRequest(), null, timeoutError);
    }
  }

  public static class WriteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ChannelHandlerContext context, @Advice.Argument(1) Object message) {
      if (message instanceof Call) {
        HbaseCallStateHelper.updateNetworkPeer(
            VirtualField.find(Call.class, RequestAndContext.class),
            (Call) message,
            context.channel().remoteAddress());
      }
    }
  }

  private HbaseCallAdvice() {}
}
