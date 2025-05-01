/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.AckType;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.support.Status;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Wrapper with an always NotNull Headers property.
 *
 * @see io.opentelemetry.instrumentation.nats.v2_21.internal.MessageTextMapSetter#set
 */
public class OpenTelemetryMessage implements Message {
  private final Connection connection;
  private final Message delegate;
  private final Headers headers;

  public OpenTelemetryMessage(Connection connection, Message delegate) {
    this.connection = connection;
    this.delegate = delegate;
    this.headers = new Headers(delegate.getHeaders());
  }

  @Override
  public String getSubject() {
    return delegate.getSubject();
  }

  @Override
  public String getReplyTo() {
    return delegate.getReplyTo();
  }

  @Override
  public boolean hasHeaders() {
    return !headers.isEmpty();
  }

  @Override
  public Headers getHeaders() {
    return this.headers;
  }

  @Override
  public boolean isStatusMessage() {
    return delegate.isStatusMessage();
  }

  @Override
  public Status getStatus() {
    return delegate.getStatus();
  }

  @Override
  public byte[] getData() {
    return delegate.getData();
  }

  @Override
  public boolean isUtf8mode() {
    return delegate.isUtf8mode();
  }

  @Override
  public Subscription getSubscription() {
    return delegate.getSubscription();
  }

  @Override
  public String getSID() {
    return delegate.getSID();
  }

  @Override
  public Connection getConnection() {
    // Connection is only set for received message.
    // To be able to expose the connection.clientId
    // in span attributes, let's link it in case of
    // a message being sent.
    Connection connection = delegate.getConnection();
    if (connection == null) {
      connection = this.connection;
    }
    return connection;
  }

  @Override
  public NatsJetStreamMetaData metaData() {
    return delegate.metaData();
  }

  @Override
  public AckType lastAck() {
    return delegate.lastAck();
  }

  @Override
  public void ack() {
    delegate.ack();
  }

  @Override
  public void ackSync(Duration timeout) throws TimeoutException, InterruptedException {
    delegate.ackSync(timeout);
  }

  @Override
  public void nak() {
    delegate.nak();
  }

  @Override
  public void nakWithDelay(Duration nakDelay) {
    delegate.nakWithDelay(nakDelay);
  }

  @SuppressWarnings("PreferJavaTimeOverload")
  @Override
  public void nakWithDelay(long nakDelayMillis) {
    delegate.nakWithDelay(nakDelayMillis);
  }

  @Override
  public void term() {
    delegate.term();
  }

  @Override
  public void inProgress() {
    delegate.inProgress();
  }

  @Override
  public boolean isJetStream() {
    return delegate.isJetStream();
  }
}
