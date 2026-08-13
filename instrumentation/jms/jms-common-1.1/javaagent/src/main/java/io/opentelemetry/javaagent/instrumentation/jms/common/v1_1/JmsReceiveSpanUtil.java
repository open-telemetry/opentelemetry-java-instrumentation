/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingReceiveTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContextHolder;
import javax.annotation.Nullable;

public class JmsReceiveSpanUtil {
  private static final ContextPropagators propagators = GlobalOpenTelemetry.getPropagators();
  private static final boolean receiveSpansEnabled =
      ExperimentalConfig.get().messagingReceiveSpansEnabled();

  public static void createReceiveSpan(
      Instrumenter<MessageWithDestination, Void> receiveInstrumenter,
      MessageWithDestination request,
      Timer timer,
      @Nullable Throwable throwable,
      boolean applicationInitiated) {
    Context parentContext = Context.current();

    if (emitStableMessagingSemconv()) {
      // under the stable messaging semantic conventions the receive operation is always recorded so
      // that its metrics flow; whether it also gets a span is decided here per the receive-spans
      // policy: spans on, and either the pull was application-initiated or a message was received
      boolean spanEligible =
          receiveSpansEnabled && (applicationInitiated || request.message() != null);
      Context receiveContext =
          MessagingReceiveTelemetry.record(
              receiveInstrumenter, parentContext, request, null, throwable, timer, spanEligible);
      if (receiveContext != null) {
        JmsReceiveContextHolder.set(receiveContext);
      }
      return;
    }

    // legacy behavior is unchanged: the consumer advice suppresses empty receives, so a message is
    // always present here. If receive spans are not enabled we'll use the producer as parent.
    if (!receiveSpansEnabled) {
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
    } else if (JmsReceiveContextHolder.isInitialized(parentContext)) {
      // the receive span was suppressed, but propagate the incoming context so a following process
      // span can parent under the producer
      Context extractedContext =
          propagators
              .getTextMapPropagator()
              .extract(Context.root(), request, MessagePropertyGetter.INSTANCE);
      JmsReceiveContextHolder.set(parentContext, extractedContext);
    }
  }

  private JmsReceiveSpanUtil() {}
}
