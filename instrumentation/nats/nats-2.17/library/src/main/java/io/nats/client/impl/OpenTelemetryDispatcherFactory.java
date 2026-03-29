/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.nats.client.impl;

import io.nats.client.MessageHandler;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import io.opentelemetry.instrumentation.nats.v2_17.internal.OpenTelemetryMessageHandler;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OpenTelemetryDispatcherFactory extends DispatcherFactory {

  private final DispatcherFactory delegate;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public OpenTelemetryDispatcherFactory(
      DispatcherFactory delegate, Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.delegate = delegate;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  @Override
  NatsDispatcher createDispatcher(NatsConnection natsConnection, MessageHandler messageHandler) {
    return delegate.createDispatcher(
        natsConnection,
        new OpenTelemetryMessageHandler(messageHandler, consumerProcessInstrumenter));
  }
}
