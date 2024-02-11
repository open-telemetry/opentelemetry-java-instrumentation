/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.async.EventPolicy;
import com.aerospike.client.async.NioEventLoops;
import com.aerospike.client.policy.ClientPolicy;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.semconv.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class AerospikeClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public static final AttributeKey<String> AEROSPIKE_STATUS = stringKey("aerospike.status");
  public static final AttributeKey<Long> AEROSPIKE_ERROR_CODE = longKey("aerospike.error.code");
  public static final AttributeKey<String> AEROSPIKE_NAMESPACE = stringKey("aerospike.namespace");
  public static final AttributeKey<String> AEROSPIKE_SET_NAME = stringKey("aerospike.set.name");
  public static final AttributeKey<String> AEROSPIKE_USER_KEY = stringKey("aerospike.user.key");
  public static final AttributeKey<Long> AEROSPIKE_TRANSFER_SIZE =
      longKey("aerospike.transfer.size");

  static GenericContainer<?> aerospikeServer =
      new GenericContainer<>("aerospike/aerospike-server:6.2.0.0")
          .withExposedPorts(3000)
          .waitingFor(Wait.forLogMessage(".*replication factor is 1.*", 1));

  static int port;

  static AerospikeClient aerospikeClient;

  @BeforeAll
  static void setupSpec() {
    aerospikeServer.start();
    port = aerospikeServer.getMappedPort(3000);
    ClientPolicy clientPolicy = new ClientPolicy();
    int eventLoopSize = Runtime.getRuntime().availableProcessors();
    System.out.println(eventLoopSize);
    EventPolicy eventPolicy = new EventPolicy();
    eventPolicy.commandsPerEventLoop = 2;
    clientPolicy.eventLoops = new NioEventLoops(eventPolicy, eventLoopSize);
    clientPolicy.maxConnsPerNode = eventLoopSize;
    clientPolicy.failIfNotConnected = true;
    aerospikeClient = new AerospikeClient(clientPolicy, "localhost", port);
  }

  @AfterAll
  static void cleanupSpec() {
    aerospikeClient.close();
    aerospikeServer.stop();
  }

  @BeforeEach
  void setup() {
    testing.clearData();
  }

  @Test
  void asyncCommand() {
    Key aerospikeKey = new Key("test", "test-set", "data1");
    aerospikeClient.put(null, null, null, aerospikeKey, new Bin("bin1", "value1"));

    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("ASYNCWRITE")
                      .hasKind(SpanKind.CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                          equalTo(SemanticAttributes.DB_OPERATION, "ASYNCWRITE"),
                          equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                          equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                          equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                          equalTo(AEROSPIKE_NAMESPACE, "test"),
                          equalTo(AEROSPIKE_SET_NAME, "test-set"),
                          equalTo(AEROSPIKE_USER_KEY, "data1"),
                          equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                          equalTo(AEROSPIKE_ERROR_CODE, 0)));
        });

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.requests",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            counter ->
                                counter.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data1"),
                                                equalTo(
                                                    SemanticAttributes.DB_OPERATION, "ASYNCWRITE"),
                                                equalTo(
                                                    SemanticAttributes.DB_SYSTEM, "aerospike"))))));
    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.response",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasName("aerospike.response")
                        .hasLongSumSatisfying(
                            counter ->
                                counter.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data1"),
                                                equalTo(
                                                    SemanticAttributes.DB_OPERATION, "ASYNCWRITE"),
                                                equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                                equalTo(AEROSPIKE_ERROR_CODE, 0),
                                                equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                                equalTo(
                                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                                    "127.0.0.1"),
                                                equalTo(
                                                    SemanticAttributes.NETWORK_TYPE, "ipv4"))))));

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.concurrency",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            upDownCounter ->
                                upDownCounter.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(0)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data1"),
                                                equalTo(
                                                    SemanticAttributes.DB_OPERATION, "ASYNCWRITE"),
                                                equalTo(
                                                    SemanticAttributes.DB_SYSTEM, "aerospike"))))));

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.client.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("ms")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AEROSPIKE_NAMESPACE, "test"),
                                            equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                            equalTo(AEROSPIKE_USER_KEY, "data1"),
                                            equalTo(SemanticAttributes.DB_OPERATION, "ASYNCWRITE"),
                                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                            equalTo(AEROSPIKE_ERROR_CODE, 0),
                                            equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                                "127.0.0.1"),
                                            equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"))))));
  }

  @Test
  void syncCommand() {
    Key aerospikeKey = new Key("test", "test-set", "data2");
    aerospikeClient.put(null, aerospikeKey, new Bin("bin2", "value2"));
    List<String> bins = singletonList("bin2");
    Record aerospikeRecord = aerospikeClient.get(null, aerospikeKey, bins.toArray(new String[0]));
    assertThat(aerospikeRecord.getString("bin2")).isEqualTo("value2");

    AtomicReference<String> instrumentationName = new AtomicReference<>();

    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("PUT")
                      .hasKind(SpanKind.CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                          equalTo(SemanticAttributes.DB_OPERATION, "PUT"),
                          equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                          equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                          equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                          equalTo(AEROSPIKE_NAMESPACE, "test"),
                          equalTo(AEROSPIKE_SET_NAME, "test-set"),
                          equalTo(AEROSPIKE_USER_KEY, "data2"),
                          equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                          equalTo(AEROSPIKE_ERROR_CODE, 0)));
        },
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("GET")
                      .hasKind(SpanKind.CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                          equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                          equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                          equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                          equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                          equalTo(AEROSPIKE_NAMESPACE, "test"),
                          equalTo(AEROSPIKE_SET_NAME, "test-set"),
                          equalTo(AEROSPIKE_USER_KEY, "data2"),
                          equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                          equalTo(AEROSPIKE_ERROR_CODE, 0),
                          equalTo(AEROSPIKE_TRANSFER_SIZE, 6)));
        });

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.requests",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            counter ->
                                counter.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data2"),
                                                equalTo(SemanticAttributes.DB_OPERATION, "PUT"),
                                                equalTo(SemanticAttributes.DB_SYSTEM, "aerospike")),
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data2"),
                                                equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                                equalTo(
                                                    SemanticAttributes.DB_SYSTEM, "aerospike"))))));

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.response",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasName("aerospike.response")
                        .hasLongSumSatisfying(
                            counter ->
                                counter.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data2"),
                                                equalTo(SemanticAttributes.DB_OPERATION, "PUT"),
                                                equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                                equalTo(AEROSPIKE_ERROR_CODE, 0),
                                                equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                                equalTo(
                                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                                    "127.0.0.1"),
                                                equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4")),
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data2"),
                                                equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                                equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                                equalTo(AEROSPIKE_ERROR_CODE, 0),
                                                equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                                equalTo(
                                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                                    "127.0.0.1"),
                                                equalTo(
                                                    SemanticAttributes.NETWORK_TYPE, "ipv4"))))));

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.concurrency",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            upDownCounter ->
                                upDownCounter.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(0)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data2"),
                                                equalTo(SemanticAttributes.DB_OPERATION, "PUT"),
                                                equalTo(SemanticAttributes.DB_SYSTEM, "aerospike")),
                                    point ->
                                        point
                                            .hasValue(0)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data2"),
                                                equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                                equalTo(
                                                    SemanticAttributes.DB_SYSTEM, "aerospike"))))));

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.client.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("ms")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AEROSPIKE_NAMESPACE, "test"),
                                            equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                            equalTo(AEROSPIKE_USER_KEY, "data2"),
                                            equalTo(SemanticAttributes.DB_OPERATION, "PUT"),
                                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                            equalTo(AEROSPIKE_ERROR_CODE, 0),
                                            equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                                "127.0.0.1"),
                                            equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4")),
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AEROSPIKE_NAMESPACE, "test"),
                                            equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                            equalTo(AEROSPIKE_USER_KEY, "data2"),
                                            equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                            equalTo(AEROSPIKE_ERROR_CODE, 0),
                                            equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                                "127.0.0.1"),
                                            equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"))))));

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        "aerospike.record.size",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("By")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasSum(6)
                                            .hasAttributesSatisfying(
                                                equalTo(AEROSPIKE_NAMESPACE, "test"),
                                                equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                                equalTo(AEROSPIKE_USER_KEY, "data2"),
                                                equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                                equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                                equalTo(AEROSPIKE_ERROR_CODE, 0),
                                                equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                                equalTo(
                                                    NetworkAttributes.NETWORK_PEER_ADDRESS,
                                                    "127.0.0.1"),
                                                equalTo(
                                                    SemanticAttributes.NETWORK_TYPE, "ipv4"))))));
  }
}
