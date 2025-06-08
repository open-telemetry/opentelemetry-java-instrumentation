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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;
import io.opentelemetry.instrumentation.nats.v2_21.internal.ThrowingSupplier;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class OpenTelemetryConnection implements Connection {

  private final Connection delegate;
  private final Instrumenter<NatsRequest, Void> producerInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;
  private final Instrumenter<NatsRequest, NatsRequest> clientInstrumenter;

  public OpenTelemetryConnection(
      Connection connection,
      Instrumenter<NatsRequest, Void> producerInstrumenter,
      Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter,
      Instrumenter<NatsRequest, NatsRequest> clientInstrumenter) {
    this.delegate = connection;
    this.producerInstrumenter = producerInstrumenter;
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  @Override
  public void publish(String subject, byte[] body) {
    wrapPublish(
        NatsRequest.create(this, null, subject, null, body), () -> delegate.publish(subject, body));
  }

  @Override
  public void publish(String subject, Headers headers, byte[] body) {
    wrapPublish(
        NatsRequest.create(this, null, subject, headers, body),
        () -> delegate.publish(subject, headers, body));
  }

  @Override
  public void publish(String subject, String replyTo, byte[] body) {
    wrapPublish(
        NatsRequest.create(this, replyTo, subject, null, body),
        () -> delegate.publish(subject, replyTo, body));
  }

  @Override
  public void publish(String subject, String replyTo, Headers headers, byte[] body) {
    wrapPublish(
        NatsRequest.create(this, replyTo, subject, headers, body),
        () -> delegate.publish(subject, replyTo, headers, body));
  }

  @Override
  public void publish(Message message) {
    wrapPublish(NatsRequest.create(this, message), () -> delegate.publish(message));
  }

  @Override
  public Message request(String subject, byte[] body, Duration timeout)
      throws InterruptedException {
    return wrapRequest(
        NatsRequest.create(this, null, subject, null, body),
        () -> delegate.request(subject, body, timeout));
  }

  @Override
  public Message request(String subject, Headers headers, byte[] body, Duration timeout)
      throws InterruptedException {
    return wrapRequest(
        NatsRequest.create(this, null, subject, headers, body),
        () -> delegate.request(subject, headers, body, timeout));
  }

  @Override
  public Message request(Message message, Duration timeout) throws InterruptedException {
    return wrapRequest(NatsRequest.create(this, message), () -> delegate.request(message, timeout));
  }

  @Override
  public CompletableFuture<Message> request(String subject, byte[] body) {
    return wrapRequest(
        NatsRequest.create(this, null, subject, null, body), () -> delegate.request(subject, body));
  }

  @Override
  public CompletableFuture<Message> request(String subject, Headers headers, byte[] body) {
    return wrapRequest(
        NatsRequest.create(this, null, subject, headers, body),
        () -> delegate.request(subject, headers, body));
  }

  @Override
  public CompletableFuture<Message> request(Message message) {
    return wrapRequest(NatsRequest.create(this, message), () -> delegate.request(message));
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(
      String subject, byte[] body, Duration timeout) {
    return wrapRequest(
        NatsRequest.create(this, null, subject, null, body),
        () -> delegate.requestWithTimeout(subject, body, timeout));
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(
      String subject, Headers headers, byte[] body, Duration timeout) {
    return wrapRequest(
        NatsRequest.create(this, null, subject, headers, body),
        () -> delegate.requestWithTimeout(subject, headers, body, timeout));
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(Message message, Duration timeout) {
    return wrapRequest(
        NatsRequest.create(this, message), () -> delegate.requestWithTimeout(message, timeout));
  }

  @Override
  public Subscription subscribe(String subject) {
    return new OpenTelemetrySubscription(
        this, delegate.subscribe(subject), consumerReceiveInstrumenter);
  }

  @Override
  public Subscription subscribe(String subject, String queueName) {
    return new OpenTelemetrySubscription(
        this, delegate.subscribe(subject, queueName), consumerReceiveInstrumenter);
  }

  @Override
  public Dispatcher createDispatcher(MessageHandler messageHandler) {
    OpenTelemetryMessageHandler otelHandler =
        new OpenTelemetryMessageHandler(
            this, messageHandler, consumerReceiveInstrumenter, consumerProcessInstrumenter);
    return new OpenTelemetryDispatcher(
        this,
        delegate.createDispatcher(otelHandler),
        consumerReceiveInstrumenter,
        consumerProcessInstrumenter);
  }

  @Override
  public Dispatcher createDispatcher() {
    return new OpenTelemetryDispatcher(
        this,
        delegate.createDispatcher(),
        consumerReceiveInstrumenter,
        consumerProcessInstrumenter);
  }

  @Override
  public void closeDispatcher(Dispatcher dispatcher) {
    if (dispatcher instanceof OpenTelemetryDispatcher) {
      delegate.closeDispatcher(((OpenTelemetryDispatcher) dispatcher).getDelegate());
      return;
    }

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
  public void flush(Duration timeout) throws TimeoutException, InterruptedException {
    delegate.flush(timeout);
  }

  @Override
  public CompletableFuture<Boolean> drain(Duration timeout)
      throws TimeoutException, InterruptedException {
    return delegate.drain(timeout);
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
  public StreamContext getStreamContext(String streamName)
      throws IOException, JetStreamApiException {
    return delegate.getStreamContext(streamName);
  }

  @Override
  public StreamContext getStreamContext(String streamName, JetStreamOptions jetStreamOptions)
      throws IOException, JetStreamApiException {
    return delegate.getStreamContext(streamName, jetStreamOptions);
  }

  @Override
  public ConsumerContext getConsumerContext(String streamName, String consumerName)
      throws IOException, JetStreamApiException {
    return delegate.getConsumerContext(streamName, consumerName);
  }

  @Override
  public ConsumerContext getConsumerContext(
      String streamName, String consumerName, JetStreamOptions jetStreamOptions)
      throws IOException, JetStreamApiException {
    return delegate.getConsumerContext(streamName, consumerName, jetStreamOptions);
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
  public KeyValue keyValue(String bucketName) throws IOException {
    return delegate.keyValue(bucketName);
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
  public ObjectStore objectStore(String bucketName) throws IOException {
    return delegate.objectStore(bucketName);
  }

  @Override
  public ObjectStore objectStore(String bucketName, ObjectStoreOptions objectStoreOptions)
      throws IOException {
    return delegate.objectStore(bucketName, objectStoreOptions);
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

  private void wrapPublish(NatsRequest natsRequest, Runnable publish) {
    Context parentContext = Context.current();

    if (!producerInstrumenter.shouldStart(parentContext, natsRequest)) {
      publish.run();
      return;
    }

    Context context = producerInstrumenter.start(parentContext, natsRequest);
    try (Scope ignored = context.makeCurrent()) {
      publish.run();
    } finally {
      producerInstrumenter.end(context, natsRequest, null, null);
    }
  }

  private Message wrapRequest(
      NatsRequest natsRequest, ThrowingSupplier<Message, InterruptedException> request)
      throws InterruptedException {
    Context parentContext = Context.current();

    if (!clientInstrumenter.shouldStart(parentContext, natsRequest)) {
      return request.call();
    }

    Context context = clientInstrumenter.start(parentContext, natsRequest);
    TimeoutException timeout = null;
    NatsRequest response = null;

    try (Scope ignored = context.makeCurrent()) {
      Message message = request.call();

      if (message == null) {
        timeout = new TimeoutException("Timed out waiting for message");
      } else {
        response = NatsRequest.create(this, message);
      }

      return message;
    } finally {
      clientInstrumenter.end(context, natsRequest, response, timeout);
    }
  }

  private CompletableFuture<Message> wrapRequest(
      NatsRequest natsRequest, Supplier<CompletableFuture<Message>> request) {
    Context parentContext = Context.current();

    if (!clientInstrumenter.shouldStart(parentContext, natsRequest)) {
      return request.get();
    }

    Context context = clientInstrumenter.start(parentContext, natsRequest);

    return request
        .get()
        .whenComplete(
            (message, exception) -> {
              if (message != null) {
                NatsRequest response = NatsRequest.create(this, message);
                clientInstrumenter.end(context, natsRequest, response, exception);
              } else {
                clientInstrumenter.end(context, natsRequest, null, exception);
              }
            });
  }
}
