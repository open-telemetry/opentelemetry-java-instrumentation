/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test for Aerospike client instrumentation.
 *
 * <p>This test is currently disabled as it requires:
 * 1. Actual Aerospike client dependency
 * 2. Aerospike server (can use Testcontainers)
 * 3. Proper integration with Vert.x
 *
 * <p>To enable this test:
 * 1. Set up Aerospike Testcontainer
 * 2. Create Aerospike client instance
 * 3. Perform operations and verify spans
 */
@Disabled("Requires Aerospike client implementation and server")
class VertxAerospikeClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static String host;
  private static String ip;
  private static int port;
  // TODO: Add Aerospike client instance
  // private static AerospikeClient client;

  @BeforeAll
  static void setup() throws Exception {
    // TODO: Set up Aerospike server using Testcontainers
    // Example:
    // GenericContainer<?> aerospike =
    //     new GenericContainer<>("aerospike:ce-6.0.0")
    //         .withExposedPorts(3000);
    // aerospike.start();
    //
    // host = aerospike.getHost();
    // ip = InetAddress.getByName(host).getHostAddress();
    // port = aerospike.getMappedPort(3000);
    //
    // client = new AerospikeClient(host, port);
  }

  @AfterAll
  static void cleanup() {
    // TODO: Clean up resources
    // if (client != null) {
    //   client.close();
    // }
  }

  @Test
  void testGetOperation() throws Exception {
    // TODO: Implement test for GET operation
    // Example:
    // Key key = new Key("test", "users", "user1");
    // Record record = client.get(null, key);
    //
    // testing.waitAndAssertTraces(
    //     trace ->
    //         trace.hasSpansSatisfyingExactly(
    //             span ->
    //                 span.hasName("GET")
    //                     .hasKind(SpanKind.CLIENT)
    //                     .hasAttributesSatisfyingExactly(
    //                         aerospikeSpanAttributes("GET", "GET test.users"))));
    //
    // if (emitStableDatabaseSemconv()) {
    //   testing.waitAndAssertMetrics(
    //       "io.opentelemetry.vertx-aerospike-client-3.9",
    //       metric -> metric.hasName("db.client.operation.duration"));
    // }
  }

  @Test
  void testPutOperation() throws Exception {
    // TODO: Implement test for PUT operation
  }

  @Test
  void testDeleteOperation() throws Exception {
    // TODO: Implement test for DELETE operation
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static AttributeAssertion[] aerospikeSpanAttributes(
      String operation, String statement) {
    if (emitStableDatabaseSemconv()) {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM_NAME, "aerospike"),
        equalTo(DB_QUERY_TEXT, statement),
        equalTo(DB_OPERATION_NAME, operation),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(NETWORK_PEER_PORT, port),
        equalTo(NETWORK_PEER_ADDRESS, ip)
      };
    } else {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM, "aerospike"),
        equalTo(DB_STATEMENT, statement),
        equalTo(DB_OPERATION, operation),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(NETWORK_PEER_PORT, port),
        equalTo(NETWORK_PEER_ADDRESS, ip)
      };
    }
  }
}

