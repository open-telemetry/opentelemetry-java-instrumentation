/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;

public class OpenTelemetryMessageHandler implements MessageHandler {

  private final Connection connection;
  private final MessageHandler delegate;
  private final Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public OpenTelemetryMessageHandler(
      Connection connection,
      MessageHandler delegate,
      Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.connection = connection;
    this.delegate = delegate;
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  @Override
  public void onMessage(Message message) throws InterruptedException {
    Timer timer = Timer.start();

    Context parentContext = Context.current();
    NatsRequest natsRequest = NatsRequest.create(connection, message);

    if (!consumerReceiveInstrumenter.shouldStart(parentContext, natsRequest)) {
      delegate.onMessage(message);
      return;
    }

    Context receiveContext =
        InstrumenterUtil.startAndEnd(
            consumerReceiveInstrumenter,
            parentContext,
            natsRequest,
            null,
            null,
            timer.startTime(),
            timer.now());

    if (!consumerProcessInstrumenter.shouldStart(receiveContext, natsRequest)) {
      delegate.onMessage(message);
      return;
    }

    Context processContext = consumerProcessInstrumenter.start(receiveContext, natsRequest);
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
