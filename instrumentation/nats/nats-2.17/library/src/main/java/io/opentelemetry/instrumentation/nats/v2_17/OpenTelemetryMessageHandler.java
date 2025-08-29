/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time. Exposed for {@link io.nats.client.impl.OpenTelemetryDispatcherFactory}.
 */
public final class OpenTelemetryMessageHandler implements MessageHandler {

  private final MessageHandler delegate;
  private final Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public OpenTelemetryMessageHandler(
      MessageHandler delegate,
      Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.delegate = delegate;
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  @Override
  public void onMessage(Message message) throws InterruptedException {
    Context parentContext = Context.current();
    NatsRequest natsRequest = NatsRequest.create(message.getConnection(), message);

    if (consumerReceiveInstrumenter.shouldStart(parentContext, natsRequest)) {
      Timer timer = Timer.start();
      parentContext =
          InstrumenterUtil.startAndEnd(
              consumerReceiveInstrumenter,
              parentContext,
              natsRequest,
              null,
              null,
              timer.startTime(),
              timer.now());
    }

    if (!consumerProcessInstrumenter.shouldStart(parentContext, natsRequest)) {
      delegate.onMessage(message);
      return;
    }

    Context processContext = consumerProcessInstrumenter.start(parentContext, natsRequest);
    InterruptedException exception = null;

    try (Scope ignored = processContext.makeCurrent()) {
      delegate.onMessage(message);
    } catch (InterruptedException e) {
      exception = e;
      throw e;
    } finally {
      consumerProcessInstrumenter.end(processContext, natsRequest, null, exception);
    }
  }
}
