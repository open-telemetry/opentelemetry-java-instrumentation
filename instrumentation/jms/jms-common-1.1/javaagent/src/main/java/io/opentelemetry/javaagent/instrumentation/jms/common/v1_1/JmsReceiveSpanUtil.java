/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContext;
import javax.annotation.Nullable;

public class JmsReceiveSpanUtil {
  public static void createReceiveSpan(
      Instrumenter<MessageWithDestination, Void> receiveInstrumenter,
      MessageWithDestination request,
      Timer timer,
      @Nullable Throwable throwable) {
    JmsMessageProcessingState processingState = request.message().prepareForReceive();
    Context parentContext = Context.current();
    if (receiveInstrumenter.shouldStart(parentContext, request)) {
      Context receiveContext =
          InstrumenterUtil.startAndEnd(
              receiveInstrumenter,
              parentContext,
              request,
              null,
              throwable,
              timer.startTime(),
              timer.now());
      request.message().setReceiveContext(new JmsReceiveContext(receiveContext, processingState));
      if (throwable == null) {
        request.message().markConsumedMessagesRecorded();
      }
    }
  }

  private JmsReceiveSpanUtil() {}
}
