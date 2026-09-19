/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContextHolder;
import javax.annotation.Nullable;

public class JmsReceiveSpanUtil {
  private static final ContextPropagators propagators = GlobalOpenTelemetry.getPropagators();
  private static final boolean receiveInstrumentationEnabled =
      ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();

  public static void createReceiveSpan(
      Instrumenter<MessageWithDestination, Void> receiveInstrumenter,
      MessageWithDestination request,
      Timer timer,
      @Nullable Throwable throwable) {
    Context parentContext = Context.current();
    // if receive instrumentation is not enabled we'll use the producer as parent, unless the stable
    // messaging semantic conventions are enabled, where the producer is linked instead
    if (!receiveInstrumentationEnabled && !emitStableMessagingSemconv()) {
      parentContext =
          propagators
              .getTextMapPropagator()
              .extract(parentContext, request, MessagePropertyGetter.INSTANCE);
    }

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
      JmsReceiveContextHolder.set(receiveContext);
      request.message().markReceiveSpanRecorded();
      // the consumed messages counter only exists under the stable conventions, and counts nothing
      // for a receive that failed, so a process operation further down still has to count this
      // message in those cases
      if (emitStableMessagingSemconv() && throwable == null) {
        request.message().markConsumedMessagesRecorded();
      }
    }
  }

  private JmsReceiveSpanUtil() {}
}
