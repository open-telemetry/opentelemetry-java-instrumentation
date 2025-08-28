/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

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
import io.nats.client.NUID;
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
import io.nats.client.support.NatsRequestCompletableFuture;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestConnection implements Connection {

  public static final String INBOX_PREFIX = "_INBOX.";

  private final NUID nuid = new NUID();
  private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

  private final List<TestSubscription> subscriptions =
      Collections.synchronizedList(new LinkedList<>());

  private final List<TestDispatcher> dispatchers = Collections.synchronizedList(new LinkedList<>());

  private final Map<String, NatsRequestCompletableFuture> pending = new ConcurrentHashMap<>();

  public TestConnection() {
    TestDispatcher inboxDispatcher =
        new TestDispatcher(
            msg -> {
              NatsRequestCompletableFuture res = pending.remove(msg.getSubject());
              if (res != null) {
                res.complete(msg);
              }
            });
    inboxDispatcher.subscribe(INBOX_PREFIX);
    dispatchers.add(inboxDispatcher);
  }

  @Override
  public void publish(String subject, byte[] body) {
    publish(NatsMessage.builder().subject(subject).data(body).build());
  }

  @Override
  public void publish(String subject, Headers headers, byte[] body) {
    publish(NatsMessage.builder().subject(subject).headers(headers).data(body).build());
  }

  @Override
  public void publish(String subject, String replyTo, byte[] body) {
    publish(NatsMessage.builder().subject(subject).replyTo(replyTo).data(body).build());
  }

  @Override
  public void publish(String subject, String replyTo, Headers headers, byte[] body) {
    publish(
        NatsMessage.builder()
            .subject(subject)
            .replyTo(replyTo)
            .headers(headers)
            .data(body)
            .build());
  }

  @Override
  public void publish(Message message) {
    internalPublish(message);
  }

  private void internalPublish(Message message) {
    // simulate async boundary, avoids passing Context between publish/dispatcher in testing
    new Thread(
            () -> {
              TestMessage msg = new TestMessage(this, null, message);
              subscriptions.forEach(sub -> sub.deliver(msg.setSubscription(sub), null));
              dispatchers.forEach(dispatcher -> dispatcher.deliver(msg));
            })
        .start();
  }

  @Override
  public Message request(String subject, byte[] body, Duration timeout)
      throws InterruptedException {
    return request(NatsMessage.builder().subject(subject).data(body).build(), timeout);
  }

  @Override
  public Message request(String subject, Headers headers, byte[] body, Duration timeout)
      throws InterruptedException {
    return request(
        NatsMessage.builder().subject(subject).headers(headers).data(body).build(), timeout);
  }

  @Override
  public Message request(Message message, Duration timeout) throws InterruptedException {
    try {
      return request(message).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException | TimeoutException | CancellationException e) {
      return null;
    }
  }

  @Override
  public CompletableFuture<Message> request(String subject, byte[] body) {
    return request(NatsMessage.builder().subject(subject).data(body).build());
  }

  @Override
  public CompletableFuture<Message> request(String subject, Headers headers, byte[] body) {
    return request(NatsMessage.builder().subject(subject).headers(headers).data(body).build());
  }

  @Override
  public CompletableFuture<Message> request(Message message) {
    return requestWithTimeout(message, null);
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(
      String subject, byte[] body, Duration timeout) {
    return requestWithTimeout(NatsMessage.builder().subject(subject).data(body).build(), timeout);
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(
      String subject, Headers headers, byte[] body, Duration timeout) {
    return requestWithTimeout(
        NatsMessage.builder().subject(subject).headers(headers).data(body).build(), timeout);
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(Message message, Duration timeout) {
    NatsRequestCompletableFuture future =
        new NatsRequestCompletableFuture(
            NatsRequestCompletableFuture.CancelAction.CANCEL, timeout, false);

    String inbox = createInbox();
    pending.put(inbox, future);

    internalPublish(
        NatsMessage.builder()
            .subject(message.getSubject())
            .replyTo(inbox)
            .headers(message.getHeaders())
            .data(message.getData())
            .build());

    if (timeout != null) {
      scheduler.schedule(
          () -> {
            if (!future.isDone()) {
              pending.remove(inbox).cancelTimedOut();
            }
          },
          timeout.toMillis(),
          TimeUnit.MILLISECONDS);
    }

    return future;
  }

  @Override
  public Subscription subscribe(String subject) {
    TestSubscription subscription = new TestSubscription(subject);
    subscriptions.add(subscription);
    return subscription;
  }

  @Override
  public Subscription subscribe(String subject, String queueName) {
    TestSubscription subscription = new TestSubscription(subject, queueName);
    subscriptions.add(subscription);
    return subscription;
  }

  @Override
  public Dispatcher createDispatcher(MessageHandler handler) {
    TestDispatcher dispatcher = new TestDispatcher(handler);
    dispatchers.add(dispatcher);
    return dispatcher;
  }

  @Override
  public Dispatcher createDispatcher() {
    TestDispatcher dispatcher = new TestDispatcher();
    dispatchers.add(dispatcher);
    return dispatcher;
  }

  @Override
  public void closeDispatcher(Dispatcher dispatcher) {
    if (dispatcher instanceof TestDispatcher && dispatchers.contains((TestDispatcher) dispatcher)) {
      dispatchers.remove(dispatcher);
      return;
    }

    throw new IllegalArgumentException("Unexpected dispatcher: " + dispatcher);
  }

  @Override
  public void addConnectionListener(ConnectionListener connectionListener) {}

  @Override
  public void removeConnectionListener(ConnectionListener connectionListener) {}

  @Override
  public void flush(Duration timeout) throws TimeoutException, InterruptedException {}

  @Override
  public CompletableFuture<Boolean> drain(Duration timeout)
      throws TimeoutException, InterruptedException {
    return null;
  }

  @Override
  public void close() throws InterruptedException {}

  @Override
  public Status getStatus() {
    return null;
  }

  @Override
  public long getMaxPayload() {
    return 0;
  }

  @Override
  public Collection<String> getServers() {
    return Collections.emptyList();
  }

  @Override
  public Statistics getStatistics() {
    return null;
  }

  @Override
  public Options getOptions() {
    return null;
  }

  @Override
  public ServerInfo getServerInfo() {
    return new ServerInfo(
        "{"
            + "\"server_id\": \"SID\", "
            + "\"server_name\": \"opentelemetry-nats\", "
            + "\"version\": \"2.10.24\", "
            + "\"go\": \"go1.23.4\", "
            + "\"host\": \"0.0.0.0\", "
            + "\"headers_supported\": true, "
            + "\"auth_required\": true, "
            + "\"nonce\": null, "
            + "\"tls_required\": false, "
            + "\"tls_available\": false, "
            + "\"ldm\": false, "
            + "\"jetstream\": false, "
            + "\"port\": 4222, "
            + "\"proto\": 1, "
            + "\"max_payload\": 1048576, "
            + "\"client_id\": 1, "
            + "\"client_ip\": \"192.168.1.1\", "
            + "\"cluster\": \"opentelemetry-nats\", "
            + "\"connect_urls\": []"
            + "}");
  }

  @Override
  public String getConnectedUrl() {
    return "";
  }

  @Override
  public InetAddress getClientInetAddress() {
    return null;
  }

  @Override
  public String getLastError() {
    return "";
  }

  @Override
  public void clearLastError() {}

  @Override
  public String createInbox() {
    return INBOX_PREFIX + nuid.next();
  }

  @Override
  public void flushBuffer() throws IOException {}

  @Override
  public void forceReconnect() throws IOException, InterruptedException {}

  @Override
  public void forceReconnect(ForceReconnectOptions options)
      throws IOException, InterruptedException {}

  @Override
  public Duration RTT() throws IOException {
    return null;
  }

  @Override
  public StreamContext getStreamContext(String streamName)
      throws IOException, JetStreamApiException {
    return null;
  }

  @Override
  public StreamContext getStreamContext(String streamName, JetStreamOptions options)
      throws IOException, JetStreamApiException {
    return null;
  }

  @Override
  public ConsumerContext getConsumerContext(String streamName, String consumerName)
      throws IOException, JetStreamApiException {
    return null;
  }

  @Override
  public ConsumerContext getConsumerContext(
      String streamName, String consumerName, JetStreamOptions options)
      throws IOException, JetStreamApiException {
    return null;
  }

  @Override
  public JetStream jetStream() throws IOException {
    return null;
  }

  @Override
  public JetStream jetStream(JetStreamOptions options) throws IOException {
    return null;
  }

  @Override
  public JetStreamManagement jetStreamManagement() throws IOException {
    return null;
  }

  @Override
  public JetStreamManagement jetStreamManagement(JetStreamOptions options) throws IOException {
    return null;
  }

  @Override
  public KeyValue keyValue(String bucketName) throws IOException {
    return null;
  }

  @Override
  public KeyValue keyValue(String bucketName, KeyValueOptions options) throws IOException {
    return null;
  }

  @Override
  public KeyValueManagement keyValueManagement() throws IOException {
    return null;
  }

  @Override
  public KeyValueManagement keyValueManagement(KeyValueOptions options) throws IOException {
    return null;
  }

  @Override
  public ObjectStore objectStore(String bucketName) throws IOException {
    return null;
  }

  @Override
  public ObjectStore objectStore(String bucketName, ObjectStoreOptions options) throws IOException {
    return null;
  }

  @Override
  public ObjectStoreManagement objectStoreManagement() throws IOException {
    return null;
  }

  @Override
  public ObjectStoreManagement objectStoreManagement(ObjectStoreOptions options)
      throws IOException {
    return null;
  }
}
