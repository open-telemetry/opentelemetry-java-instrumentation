/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.singletonList;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.async.EventLoops;
import com.aerospike.client.async.EventPolicy;
import com.aerospike.client.async.NioEventLoops;
import com.aerospike.client.policy.ClientPolicy;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.db.AerospikeSemanticAttributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
class AerospikeClientAsyncCommandTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

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
    EventPolicy eventPolicy = new EventPolicy();
    eventPolicy.commandsPerEventLoop = 2;
    EventLoops eventLoops = new NioEventLoops(eventPolicy, eventLoopSize);
    clientPolicy.eventLoops = eventLoops;
    clientPolicy.maxConnsPerNode = 11;
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
  void putCommand() {
    Key aerospikeKey = new Key("test", "test-set", "data1");
    aerospikeClient.put(null, null, null, aerospikeKey, new Bin("bin1", "value1"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ASYNCWRITE")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                            equalTo(SemanticAttributes.DB_OPERATION, "ASYNCWRITE"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE, "test"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_SET_NAME, "test-set"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_USER_KEY, "data1"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_STATUS, "SUCCESS"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE, 0),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_NAME,
                                val -> val.isIn("localhost", "127.0.0.1")))));
  }

  @Test
  void getCommand() {
    Key aerospikeKey = new Key("test", "test-set", "data1");
    List<String> bins = singletonList("bin1");
    aerospikeClient.get(null, null, null, aerospikeKey, bins.toArray(new String[0]));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ASYNCREAD")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                            equalTo(SemanticAttributes.DB_OPERATION, "ASYNCREAD"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE, "test"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_SET_NAME, "test-set"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_USER_KEY, "data1"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_STATUS, "SUCCESS"),
                            equalTo(AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE, 0),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_NAME,
                                val -> val.isIn("localhost", "127.0.0.1")))));
  }
}
