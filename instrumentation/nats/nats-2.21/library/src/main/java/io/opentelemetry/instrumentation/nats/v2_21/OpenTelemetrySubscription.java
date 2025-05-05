/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;
import io.opentelemetry.instrumentation.nats.v2_21.internal.ThrowingSupplier2;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class OpenTelemetrySubscription implements Subscription {

  private final Connection connection;
  private final Subscription delegate;
  private final Instrumenter<NatsRequest, Void> consumerInstrumenter;

  public OpenTelemetrySubscription(
      Connection connection,
      Subscription subscription,
      Instrumenter<NatsRequest, Void> consumerInstrumenter) {
    this.connection = connection;
    this.delegate = subscription;
    this.consumerInstrumenter = consumerInstrumenter;
  }

  @Override
  public String getSubject() {
    return delegate.getSubject();
  }

  @Override
  public String getQueueName() {
    return delegate.getQueueName();
  }

  @Override
  public Dispatcher getDispatcher() {
    return delegate.getDispatcher();
  }

  @SuppressWarnings("ThrowsUncheckedException")
  @Override
  public Message nextMessage(Duration timeout) throws InterruptedException, IllegalStateException {
    return wrapNextMessage(() -> delegate.nextMessage(timeout));
  }

  @SuppressWarnings({"PreferJavaTimeOverload", "ThrowsUncheckedException"})
  @Override
  public Message nextMessage(long timeoutMillis)
      throws InterruptedException, IllegalStateException {
    return wrapNextMessage(() -> delegate.nextMessage(timeoutMillis));
  }

  @Override
  public void unsubscribe() {
    delegate.unsubscribe();
  }

  @Override
  public Subscription unsubscribe(int after) {
    return delegate.unsubscribe(after);
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

  private Message wrapNextMessage(
      ThrowingSupplier2<Message, InterruptedException, IllegalStateException> nextMessage)
      throws InterruptedException {
    Timer timer = Timer.start();
    Message message = nextMessage.call();

    Context parentContext = Context.current();
    TimeoutException timeout = null;
    NatsRequest natsRequest = NatsRequest.create(this.connection, this.getSubject());

    if (message == null) {
      timeout = new TimeoutException("Timed out waiting for message");
    } else {
      natsRequest = NatsRequest.create(this.connection, message);
    }

    if (!Span.fromContext(parentContext).getSpanContext().isValid()
        || !consumerInstrumenter.shouldStart(parentContext, natsRequest)) {
      return message;
    }

    InstrumenterUtil.startAndEnd(
        consumerInstrumenter,
        parentContext,
        natsRequest,
        null,
        timeout,
        timer.startTime(),
        timer.now());

    return message;
  }
}
