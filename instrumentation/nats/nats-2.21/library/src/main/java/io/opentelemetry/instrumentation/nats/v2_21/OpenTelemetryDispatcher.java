/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class OpenTelemetryDispatcher implements Dispatcher {

  private final Connection connection;
  private final Dispatcher delegate;
  private final Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public OpenTelemetryDispatcher(
      Connection connection,
      Dispatcher delegate,
      Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.connection = connection;
    this.delegate = delegate;
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  @Override
  public void start(String id) {
    delegate.start(id);
  }

  @Override
  public Dispatcher subscribe(String subject) {
    delegate.subscribe(subject);
    // from javadoc - Returns: The Dispatcher, so calls can be chained.
    return this;
  }

  @Override
  public Dispatcher subscribe(String subject, String queue) {
    delegate.subscribe(subject, queue);
    // from javadoc - Returns: The Dispatcher, so calls can be chained.
    return this;
  }

  @Override
  public Subscription subscribe(String subject, MessageHandler handler) {
    OpenTelemetryMessageHandler otelHandler =
        new OpenTelemetryMessageHandler(
            connection, handler, consumerReceiveInstrumenter, consumerProcessInstrumenter);
    Subscription wrapped = delegate.subscribe(subject, otelHandler);
    return new OpenTelemetrySubscription(connection, wrapped, consumerReceiveInstrumenter);
  }

  @Override
  public Subscription subscribe(String subject, String queue, MessageHandler handler) {
    OpenTelemetryMessageHandler otelHandler =
        new OpenTelemetryMessageHandler(
            connection, handler, consumerReceiveInstrumenter, consumerProcessInstrumenter);
    Subscription wrapped = delegate.subscribe(subject, queue, otelHandler);
    return new OpenTelemetrySubscription(connection, wrapped, consumerReceiveInstrumenter);
  }

  @Override
  public Dispatcher unsubscribe(String subject) {
    delegate.unsubscribe(subject);
    // from javadoc - Returns: The Dispatcher, so calls can be chained.
    return this;
  }

  @Override
  public Dispatcher unsubscribe(Subscription subscription) {
    if (subscription instanceof OpenTelemetrySubscription) {
      delegate.unsubscribe(((OpenTelemetrySubscription) subscription).getDelegate());
    } else {
      delegate.unsubscribe(subscription);
    }

    // from javadoc - Returns: The Dispatcher, so calls can be chained.
    return this;
  }

  @Override
  public Dispatcher unsubscribe(String subject, int after) {
    delegate.unsubscribe(subject, after);
    // from javadoc - Returns: The Dispatcher, so calls can be chained.
    return this;
  }

  @Override
  public Dispatcher unsubscribe(Subscription subscription, int after) {
    if (subscription instanceof OpenTelemetrySubscription) {
      delegate.unsubscribe(((OpenTelemetrySubscription) subscription).getDelegate(), after);
    } else {
      delegate.unsubscribe(subscription, after);
    }

    // from javadoc - Returns: The Dispatcher, so calls can be chained.
    return this;
  }

  @Override
  public void setPendingLimits(long maxMessages, long maxBytes) {
    delegate.setPendingLimits(maxMessages, maxBytes);
  }

  @Override
  public long getPendingMessageLimit() {
    return delegate.getPendingMessageLimit();
  }

  @Override
  public long getPendingByteLimit() {
    return delegate.getPendingByteLimit();
  }

  @Override
  public long getPendingMessageCount() {
    return delegate.getPendingMessageCount();
  }

  @Override
  public long getPendingByteCount() {
    return delegate.getPendingByteCount();
  }

  @Override
  public long getDeliveredCount() {
    return delegate.getDeliveredCount();
  }

  @Override
  public long getDroppedCount() {
    return delegate.getDroppedCount();
  }

  @Override
  public void clearDroppedCount() {
    delegate.clearDroppedCount();
  }

  @Override
  public boolean isActive() {
    return delegate.isActive();
  }

  @Override
  public CompletableFuture<Boolean> drain(Duration timeout) throws InterruptedException {
    return delegate.drain(timeout);
  }

  protected Dispatcher getDelegate() {
    return delegate;
  }
}
