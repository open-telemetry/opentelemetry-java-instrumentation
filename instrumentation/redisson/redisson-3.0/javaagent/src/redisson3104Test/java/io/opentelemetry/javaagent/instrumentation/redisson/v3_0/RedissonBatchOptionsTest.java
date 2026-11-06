/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.redisson.api.BatchOptions.ExecutionMode.IN_MEMORY;
import static org.redisson.api.BatchOptions.ExecutionMode.IN_MEMORY_ATOMIC;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.reactivex.subscribers.TestSubscriber;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.Redisson;
import org.redisson.api.BatchOptions;
import org.redisson.api.RBatch;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RBatchRx;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedissonBatchOptionsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private final Config config = new Config();
  private String host;
  private String ip;
  private long port;

  @BeforeAll
  void setup() throws UnknownHostException {
    cleanup.deferAfterAll(redisServer::stop);
    redisServer.start();
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379).longValue();
    config.setCodec(StringCodec.INSTANCE);
    config
        .useSingleServer()
        .setAddress("redis://" + host + ":" + port)
        .setConnectionMinimumIdleSize(1)
        .setConnectionPoolSize(1)
        .setSubscriptionConnectionMinimumIdleSize(0)
        .setPingConnectionInterval(0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reactiveBatch(boolean atomic) {
    RedissonReactiveClient client = Redisson.createReactive(config);
    deferShutdown(client::shutdown);
    RBatchReactive batch =
        client.createBatch(
            BatchOptions.defaults().executionMode(atomic ? IN_MEMORY_ATOMIC : IN_MEMORY));
    batch.getBucket("batch1").set("v1");
    batch.getBucket("batch2").set("v2");
    assertThat(batch.execute().block().getResponses()).hasSize(2);

    assertBatch(atomic);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rxBatch(boolean atomic) {
    RedissonRxClient client = Redisson.createRx(config);
    deferShutdown(client::shutdown);
    RBatchRx batch =
        client.createBatch(
            BatchOptions.defaults().executionMode(atomic ? IN_MEMORY_ATOMIC : IN_MEMORY));
    TestSubscriber<Void> first = batch.getBucket("batch1").set("v1").test();
    TestSubscriber<Void> second = batch.getBucket("batch2").set("v2").test();
    assertThat(batch.execute().blockingFirst().getResponses()).hasSize(2);
    first.assertResult();
    second.assertResult();

    assertBatch(atomic);
  }

  @Test
  void constructorOptionsTakePrecedenceOverExecuteDefaults() {
    RedissonClient client = Redisson.create(config);
    deferShutdown(client::shutdown);
    RBatch batch = client.createBatch(BatchOptions.defaults().executionMode(IN_MEMORY_ATOMIC));
    batch.getBucket("batch1").setAsync("v1");
    batch.getBucket("batch2").setAsync("v2");
    assertThat(batch.execute().getResponses()).hasSize(2);

    assertBatch(true);
  }

  private void assertBatch(boolean atomic) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(atomic ? "MULTI SET" : "PIPELINE SET " + host + ":" + port)
                        .hasKind(CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "redis"),
                            equalTo(DB_OPERATION_NAME, atomic ? "MULTI SET" : "PIPELINE SET"),
                            equalTo(DB_OPERATION_BATCH_SIZE, 2),
                            equalTo(DB_QUERY_TEXT, "SET batch1 ?; SET batch2 ?"),
                            equalTo(SERVER_ADDRESS, atomic ? null : host),
                            equalTo(SERVER_PORT, atomic ? null : port),
                            equalTo(NETWORK_PEER_ADDRESS, atomic ? null : ip),
                            equalTo(NETWORK_PEER_PORT, atomic ? null : port))));
  }

  private void deferShutdown(Runnable shutdown) {
    cleanup.deferCleanup(
        () -> {
          shutdown.run();
          // Wait for the batch trace and the single connection's QUIT trace before the next test.
          testing.waitForTraces(2);
        });
  }
}
