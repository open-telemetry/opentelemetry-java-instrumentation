/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.AckType;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.support.Status;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

public class TestMessage implements Message {

  private final Connection connection;
  private Subscription subscription;
  private final Message message;

  public TestMessage(Connection connection, Subscription subscription, Message message) {
    this.connection = connection;
    this.subscription = subscription;
    this.message = message;
  }

  @Override
  public String getSubject() {
    return message.getSubject();
  }

  @Override
  public String getReplyTo() {
    return message.getReplyTo();
  }

  @Override
  public boolean hasHeaders() {
    return message.hasHeaders();
  }

  @Override
  public Headers getHeaders() {
    return message.getHeaders();
  }

  @Override
  public boolean isStatusMessage() {
    return message.isStatusMessage();
  }

  @Override
  public Status getStatus() {
    return message.getStatus();
  }

  @Override
  public byte[] getData() {
    return message.getData();
  }

  @Override
  public boolean isUtf8mode() {
    return message.isUtf8mode();
  }

  @Override
  public Subscription getSubscription() {
    return subscription;
  }

  @Override
  public String getSID() {
    return subscription.toString();
  }

  @Override
  public Connection getConnection() {
    return connection;
  }

  @Override
  public NatsJetStreamMetaData metaData() {
    return message.metaData();
  }

  @Override
  public AckType lastAck() {
    return message.lastAck();
  }

  @Override
  public void ack() {
    message.ack();
  }

  @Override
  public void ackSync(Duration timeout) throws TimeoutException, InterruptedException {
    message.ackSync(timeout);
  }

  @Override
  public void nak() {
    message.nak();
  }

  @Override
  public void nakWithDelay(Duration nakDelay) {
    message.nakWithDelay(nakDelay);
  }

  @SuppressWarnings("PreferJavaTimeOverload")
  @Override
  public void nakWithDelay(long nakDelayMillis) {
    message.nakWithDelay(nakDelayMillis);
  }

  @Override
  public void term() {
    message.term();
  }

  @Override
  public void inProgress() {
    message.inProgress();
  }

  @Override
  public boolean isJetStream() {
    return message.isJetStream();
  }

  public TestMessage setSubscription(Subscription subscription) {
    this.subscription = subscription;
    return this;
  }
}
