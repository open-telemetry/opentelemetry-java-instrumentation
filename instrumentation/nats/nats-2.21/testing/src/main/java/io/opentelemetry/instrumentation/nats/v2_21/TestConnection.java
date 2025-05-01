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
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class TestConnection implements Connection {

  public final LinkedList<Message> publishedMessages = new LinkedList<>();

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
    publishedMessages.add(message);
  }

  @Override
  public CompletableFuture<Message> request(String subject, byte[] body) {
    return null;
  }

  @Override
  public Message request(String subject, byte[] body, Duration timeout)
      throws InterruptedException {
    return null;
  }

  @Override
  public CompletableFuture<Message> request(String subject, Headers headers, byte[] body) {
    return null;
  }

  @Override
  public Message request(String subject, Headers headers, byte[] body, Duration timeout)
      throws InterruptedException {
    return null;
  }

  @Override
  public CompletableFuture<Message> request(Message message) {
    return null;
  }

  @Override
  public Message request(Message message, Duration timeout) throws InterruptedException {
    return null;
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(
      String subject, byte[] body, Duration timeout) {
    return null;
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(
      String subject, Headers headers, byte[] body, Duration timeout) {
    return null;
  }

  @Override
  public CompletableFuture<Message> requestWithTimeout(Message message, Duration timeout) {
    return null;
  }

  @Override
  public Subscription subscribe(String subject) {
    return null;
  }

  @Override
  public Subscription subscribe(String subject, String queueName) {
    return null;
  }

  @Override
  public Dispatcher createDispatcher(MessageHandler handler) {
    return null;
  }

  @Override
  public Dispatcher createDispatcher() {
    return null;
  }

  @Override
  public void closeDispatcher(Dispatcher dispatcher) {}

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
    return "";
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
