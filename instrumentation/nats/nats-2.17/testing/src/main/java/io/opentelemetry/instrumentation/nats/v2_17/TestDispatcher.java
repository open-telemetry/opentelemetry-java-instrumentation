/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.TestConnection.INBOX_PREFIX;

import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TestDispatcher implements Dispatcher {

  private final MessageHandler defaultHandler;
  private final List<String> subjects = Collections.synchronizedList(new LinkedList<>());
  private final Map<TestSubscription, MessageHandler> subscriptions =
      Collections.synchronizedMap(new HashMap<>());

  public TestDispatcher() {
    defaultHandler = msg -> {};
  }

  public TestDispatcher(MessageHandler defaultHandler) {
    this.defaultHandler = defaultHandler;
  }

  @SuppressWarnings("EmptyCatch")
  public void deliver(TestMessage message) {
    subjects.forEach(
        subject -> {
          if (message.getSubject().equalsIgnoreCase(subject)
              || (subject.equals(INBOX_PREFIX) && message.getSubject().startsWith(INBOX_PREFIX))) {
            try {
              defaultHandler.onMessage(message);
            } catch (InterruptedException ignored) {
            }
          }
        });
    subscriptions.forEach((sub, handler) -> sub.deliver(message.setSubscription(sub), handler));
  }

  @Override
  public void start(String id) {}

  @Override
  public Dispatcher subscribe(String subject) {
    subjects.add(subject);
    return this;
  }

  @Override
  public Dispatcher subscribe(String subject, String queue) {
    subjects.add(subject);
    return this;
  }

  @Override
  public Subscription subscribe(String subject, MessageHandler handler) {
    TestSubscription subscription = new TestSubscription(subject, null, this);
    subscriptions.put(subscription, handler);
    return subscription;
  }

  @Override
  public Subscription subscribe(String subject, String queue, MessageHandler handler) {
    TestSubscription subscription = new TestSubscription(subject, queue, this);
    subscriptions.put(subscription, handler);
    return subscription;
  }

  @Override
  public Dispatcher unsubscribe(String subject) {
    if (subjects.contains(subject)) {
      subjects.remove(subject);
      return this;
    }
    throw new IllegalStateException("Cannot unsubscribe to " + subject);
  }

  @Override
  public Dispatcher unsubscribe(Subscription subscription) {
    if (subscription instanceof TestSubscription
        && subscriptions.containsKey((TestSubscription) subscription)) {
      subscriptions.remove(subscription);
      return this;
    }
    throw new IllegalArgumentException("Unexpected subscription: " + subscription);
  }

  @Override
  public Dispatcher unsubscribe(String subject, int after) {
    if (subjects.contains(subject)) {
      subjects.remove(subject);
      return this;
    }
    throw new IllegalStateException("Cannot unsubscribe to " + subject);
  }

  @Override
  public Dispatcher unsubscribe(Subscription subscription, int after) {
    if (subscription instanceof TestSubscription
        && subscriptions.containsKey((TestSubscription) subscription)) {
      subscriptions.remove(subscription);
      return this;
    }
    throw new IllegalArgumentException("Unexpected subscription: " + subscription);
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
