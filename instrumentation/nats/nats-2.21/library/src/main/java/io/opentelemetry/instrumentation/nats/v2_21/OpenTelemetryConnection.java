/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.ConsumerContext;
import io.nats.client.Dispatcher;
import io.nats.client.ForceReconnectOptions;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamOptions;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.KeyValueOptions;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.ObjectStore;
import io.nats.client.ObjectStoreManagement;
import io.nats.client.ObjectStoreOptions;
import io.nats.client.Options;
import io.nats.client.Statistics;
import io.nats.client.StreamContext;
import io.nats.client.Subscription;
import io.nats.client.api.ServerInfo;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class OpenTelemetryConnection implements Connection {

  private final Connection delegate;
  private final Instrumenter<Message, Void> producerInstrumenter;

  public OpenTelemetryConnection(
      Connection connection, Instrumenter<Message, Void> producerInstrumenter) {
    this.delegate = connection;
    this.producerInstrumenter = producerInstrumenter;
  }

  @Override
  public void publish(String subject, byte[] body) {
    this.publish(NatsMessage.builder().subject(subject).data(body).build());
  }

  @Override
  public void publish(String subject, Headers headers, byte[] body) {
    this.publish(NatsMessage.builder().subject(subject).headers(headers).data(body).build());
  }

  @Override
  public void publish(String subject, String replyTo, byte[] body) {
    this.publish(NatsMessage.builder().subject(subject).replyTo(replyTo).data(body).build());
  }

  @Override
  public void publish(String subject, String replyTo, Headers headers, byte[] body) {
    this.publish(
        NatsMessage.builder()
            .subject(subject)
            .replyTo(replyTo)
            .headers(headers)
            .data(body)
            .build());
  }

  @Override
  public void publish(Message message) {
    Context parentContext = Context.current();

    if (!Span.fromContext(parentContext).getSpanContext().isValid()
        || !producerInstrumenter.shouldStart(parentContext, message)) {
      delegate.publish(message);
      return;
    }

    Message otelMessage = new OpenTelemetryMessage(this, message);
    Context context = producerInstrumenter.start(parentContext, otelMessage);

    try (Scope ignored = context.makeCurrent()) {
      delegate.publish(otelMessage);
    } finally {
      producerInstrumenter.end(context, otelMessage, null, null);
    }
  }

  @Override
  public CompletableFuture<Message> request(String s, byte[] bytes) {
    return delegate.request(s, bytes);
  }

  @Override
  public Message request(String s, byte[] bytes, Duration duration) throws InterruptedException {
    return delegate.request(s, bytes, duration);
  }

  @Override
  public CompletableFuture<Message> request(String s, Headers headers, byte[] bytes) {
    return delegate.request(s, headers, bytes);
  }

  @Override
  public Message request(String s, Headers headers, byte[] bytes, Duration duration)
      throws InterruptedException {
    return delegate.request(s, headers, bytes, duration);
  }

  @Override
  public CompletableFuture<Message> request(Message message) {
    return delegate.request(message);
  }

  @Override
  public Message request(Message message, Duration duration) throws InterruptedException {
    return delegate.request(message, duration);
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(String s, byte[] bytes, Duration duration) {
    return delegate.requestWithTimeout(s, bytes, duration);
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(
      String s, Headers headers, byte[] bytes, Duration duration) {
    return delegate.requestWithTimeout(s, headers, bytes, duration);
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(Message message, Duration duration) {
    return delegate.requestWithTimeout(message, duration);
  }

  @Override
  public Subscription subscribe(String s) {
    return delegate.subscribe(s);
  }

  @Override
  public Subscription subscribe(String s, String s1) {
    return delegate.subscribe(s, s1);
  }

  @Override
  public Dispatcher createDispatcher(MessageHandler messageHandler) {
    return delegate.createDispatcher(messageHandler);
  }

  @Override
  public Dispatcher createDispatcher() {
    return delegate.createDispatcher();
  }

  @Override
  public void closeDispatcher(Dispatcher dispatcher) {
    delegate.closeDispatcher(dispatcher);
  }

  @Override
  public void addConnectionListener(ConnectionListener connectionListener) {
    delegate.addConnectionListener(connectionListener);
  }

  @Override
  public void removeConnectionListener(ConnectionListener connectionListener) {
    delegate.removeConnectionListener(connectionListener);
  }

  @Override
  public void flush(Duration duration) throws TimeoutException, InterruptedException {
    delegate.flush(duration);
  }

  @Override
  public CompletableFuture<Boolean> drain(Duration duration)
      throws TimeoutException, InterruptedException {
    return delegate.drain(duration);
  }

  @Override
  public void close() throws InterruptedException {
    delegate.close();
  }

  @Override
  public Status getStatus() {
    return delegate.getStatus();
  }

  @Override
  public long getMaxPayload() {
    return delegate.getMaxPayload();
  }

  @Override
  public Collection<String> getServers() {
    return delegate.getServers();
  }

  @Override
  public Statistics getStatistics() {
    return delegate.getStatistics();
  }

  @Override
  public Options getOptions() {
    return delegate.getOptions();
  }

  @Override
  public ServerInfo getServerInfo() {
    return delegate.getServerInfo();
  }

  @Override
  public String getConnectedUrl() {
    return delegate.getConnectedUrl();
  }

  @Override
  public InetAddress getClientInetAddress() {
    return delegate.getClientInetAddress();
  }

  @Override
  public String getLastError() {
    return delegate.getLastError();
  }

  @Override
  public void clearLastError() {
    delegate.clearLastError();
  }

  @Override
  public String createInbox() {
    return delegate.createInbox();
  }

  @Override
  public void flushBuffer() throws IOException {
    delegate.flushBuffer();
  }

  @Override
  public void forceReconnect() throws IOException, InterruptedException {
    delegate.forceReconnect();
  }

  @Override
  public void forceReconnect(ForceReconnectOptions forceReconnectOptions)
      throws IOException, InterruptedException {
    delegate.forceReconnect(forceReconnectOptions);
  }

  @Override
  public Duration RTT() throws IOException {
    return delegate.RTT();
  }

  @Override
  public StreamContext getStreamContext(String s) throws IOException, JetStreamApiException {
    return delegate.getStreamContext(s);
  }

  @Override
  public StreamContext getStreamContext(String s, JetStreamOptions jetStreamOptions)
      throws IOException, JetStreamApiException {
    return delegate.getStreamContext(s, jetStreamOptions);
  }

  @Override
  public ConsumerContext getConsumerContext(String s, String s1)
      throws IOException, JetStreamApiException {
    return delegate.getConsumerContext(s, s1);
  }

  @Override
  public ConsumerContext getConsumerContext(String s, String s1, JetStreamOptions jetStreamOptions)
      throws IOException, JetStreamApiException {
    return delegate.getConsumerContext(s, s1, jetStreamOptions);
  }

  @Override
  public JetStream jetStream() throws IOException {
    return delegate.jetStream();
  }

  @Override
  public JetStream jetStream(JetStreamOptions jetStreamOptions) throws IOException {
    return delegate.jetStream(jetStreamOptions);
  }

  @Override
  public JetStreamManagement jetStreamManagement() throws IOException {
    return delegate.jetStreamManagement();
  }

  @Override
  public JetStreamManagement jetStreamManagement(JetStreamOptions jetStreamOptions)
      throws IOException {
    return delegate.jetStreamManagement(jetStreamOptions);
  }

  @Override
  public KeyValue keyValue(String s) throws IOException {
    return delegate.keyValue(s);
  }

  @Override
  public KeyValue keyValue(String s, KeyValueOptions keyValueOptions) throws IOException {
    return delegate.keyValue(s, keyValueOptions);
  }

  @Override
  public KeyValueManagement keyValueManagement() throws IOException {
    return delegate.keyValueManagement();
  }

  @Override
  public KeyValueManagement keyValueManagement(KeyValueOptions keyValueOptions) throws IOException {
    return delegate.keyValueManagement(keyValueOptions);
  }

  @Override
  public ObjectStore objectStore(String s) throws IOException {
    return delegate.objectStore(s);
  }

  @Override
  public ObjectStore objectStore(String s, ObjectStoreOptions objectStoreOptions)
      throws IOException {
    return delegate.objectStore(s, objectStoreOptions);
  }

  @Override
  public ObjectStoreManagement objectStoreManagement() throws IOException {
    return delegate.objectStoreManagement();
  }

  @Override
  public ObjectStoreManagement objectStoreManagement(ObjectStoreOptions objectStoreOptions)
      throws IOException {
    return delegate.objectStoreManagement(objectStoreOptions);
  }
}
