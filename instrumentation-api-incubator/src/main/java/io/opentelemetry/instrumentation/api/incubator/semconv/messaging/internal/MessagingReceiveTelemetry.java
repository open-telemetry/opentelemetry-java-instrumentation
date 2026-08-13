/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import javax.annotation.Nullable;

/**
 * Records a messaging pull operation, creating a receive span for it only when it is eligible for
 * one.
 *
 * <p>Metrics are recorded either way. Whether a span is created is decided by the caller, which
 * combines the receive spans setting with whether the pull was initiated by the application: a span
 * is eligible when receive spans are enabled and either the pull was application-initiated or it
 * returned at least one message.
 *
 * <p>An application-initiated pull is a call the user made, so it is worth a span whether or not it
 * returned messages. An internal listener polling loop is not, so it only gets a span when the poll
 * actually produced something to correlate to.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingReceiveTelemetry {

  /**
   * Records the pull operation described by {@code request} against {@code instrumenter}, using
   * {@code timer} for the start and end timestamps.
   *
   * @return the receive context when a receive span was created, {@code null} otherwise. Callers
   *     that parent process spans under the receive span should link to the producer instead when
   *     this returns {@code null}.
   */
  @Nullable
  public static <REQUEST, RESPONSE> Context record(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context parentContext,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error,
      Timer timer,
      boolean spanEligible) {
    if (!instrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    if (!spanEligible) {
      InstrumenterUtil.startAndEndWithoutSpan(
          instrumenter, parentContext, request, response, error, timer.startTime(), timer.now());
      return null;
    }
    return InstrumenterUtil.startAndEnd(
        instrumenter, parentContext, request, response, error, timer.startTime(), timer.now());
  }

  private MessagingReceiveTelemetry() {}
}
