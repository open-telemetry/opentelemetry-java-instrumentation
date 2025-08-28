/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestSubscription implements Subscription {
  private final String subject;
  private final String queueName;
  private final Dispatcher dispatcher;

  public final Queue<Message> messages = new ConcurrentLinkedQueue<>();

  public TestSubscription(String subject) {
    this.subject = subject;
    this.queueName = null;
    this.dispatcher = null;
  }

  public TestSubscription(String subject, String queueName) {
    this.subject = subject;
    this.queueName = queueName;
    this.dispatcher = null;
  }

  public TestSubscription(String subject, String queueName, Dispatcher dispatcher) {
    this.subject = subject;
    this.queueName = queueName;
    this.dispatcher = dispatcher;
  }

  @SuppressWarnings({"EmptyCatchBlock", "EmptyCatch"})
  public void deliver(TestMessage message, MessageHandler handler) {
    if (message.getSubject().equalsIgnoreCase(getSubject())) {
      messages.add(message);
      if (handler != null) {
        try {
          handler.onMessage(message);
        } catch (InterruptedException e) {
        }
      }
    }
  }

  @Override
  public String getSubject() {
    return subject;
  }

  @Override
  public String getQueueName() {
    return queueName;
  }

  @Override
  public Dispatcher getDispatcher() {
    return dispatcher;
  }

  @SuppressWarnings("ThrowsUncheckedException")
  @Override
  public Message nextMessage(Duration timeout) throws InterruptedException, IllegalStateException {
    if (dispatcher != null) {
      throw new IllegalStateException(
          "Subscriptions that belong to a dispatcher cannot respond to nextMessage directly.");
    }

    return messages.poll();
  }

  @SuppressWarnings("ThrowsUncheckedException")
  @Override
  public Message nextMessage(long timeoutMillis)
      throws InterruptedException, IllegalStateException {
    if (dispatcher != null) {
      throw new IllegalStateException(
          "Subscriptions that belong to a dispatcher cannot respond to nextMessage directly.");
    }

    return messages.poll();
  }

  @Override
  public void unsubscribe() {
    if (dispatcher != null) {
      dispatcher.unsubscribe(this);
      return;
    }
    throw new IllegalStateException(
        "Subscriptions that belong to a dispatcher cannot be unsubscribed.");
  }

  @Override
  public Subscription unsubscribe(int after) {
    if (dispatcher != null) {
      dispatcher.unsubscribe(this, after);
      return this;
    }
    throw new IllegalStateException(
        "Subscriptions that belong to a dispatcher cannot be unsubscribed.");
  }

  @Override
  public void setPendingLimits(long maxMessages, long maxBytes) {}

  @Override
  public long getPendingMessageLimit() {
    return 0;
  }

  @Override
  public long getPendingByteLimit() {
    return 0;
  }

  @Override
  public long getPendingMessageCount() {
    return 0;
  }

  @Override
  public long getPendingByteCount() {
    return 0;
  }

  @Override
  public long getDeliveredCount() {
    return 0;
  }

  @Override
  public long getDroppedCount() {
    return 0;
  }

  @Override
  public void clearDroppedCount() {}

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public CompletableFuture<Boolean> drain(Duration timeout) throws InterruptedException {
    return null;
  }
}
