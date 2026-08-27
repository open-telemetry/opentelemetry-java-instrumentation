/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time. Exposed for {@link io.nats.client.impl.OpenTelemetryDispatcherFactory}.
 */
public final class OpenTelemetryMessageHandler implements MessageHandler {

  private final MessageHandler delegate;
  private final Instrumenter<NatsRequest, NatsRequest> settleInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public OpenTelemetryMessageHandler(
      MessageHandler delegate,
      Instrumenter<NatsRequest, NatsRequest> settleInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.delegate = delegate;
    this.settleInstrumenter = settleInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  @Override
  public void onMessage(Message message) throws InterruptedException {
    Context parentContext = Context.current();
    NatsRequest natsRequest = NatsRequest.create(message.getConnection(), message);

    if (!consumerProcessInstrumenter.shouldStart(parentContext, natsRequest)) {
      delegate.onMessage(OpenTelemetryMessage.wrap(message, settleInstrumenter));
      return;
    }

    Context processContext = consumerProcessInstrumenter.start(parentContext, natsRequest);
    Throwable error = null;

    try (Scope ignored = processContext.makeCurrent()) {
      delegate.onMessage(OpenTelemetryMessage.wrap(message, settleInstrumenter));
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      consumerProcessInstrumenter.end(processContext, natsRequest, null, error);
    }
  }
}
