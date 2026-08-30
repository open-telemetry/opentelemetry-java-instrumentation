/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static java.util.Collections.singleton;

import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.env.TimeoutConfig;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.env.ClusterEnvironment;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.couchbase.CouchbaseService;

// Couchbase instrumentation is owned upstream, so limited testing is performed here.
@SuppressWarnings("deprecation") // using deprecated semconv
class CouchbaseClient31Test {
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.couchbase.experimental-span-attributes");

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("couchbase-container");

  private static CouchbaseContainer couchbase;
  private static String seedAddress;
  private static int kvPort;
  private static int clusterManagerPort;
  private static Cluster cluster;
  private static Collection collection;

  @BeforeAll
  static void setup() {
    couchbase =
        new CouchbaseContainer("couchbase/server:7.6.0")
            .withExposedPorts(8091)
            .withEnabledServices(CouchbaseService.KV)
            .withBucket(new BucketDefinition("test"))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupAttempts(5)
            .withStartupTimeout(Duration.ofMinutes(2));
    couchbase.start();
    cleanup.deferAfterAll(couchbase::stop);

    ClusterEnvironment environment =
        ClusterEnvironment.builder()
            .timeoutConfig(TimeoutConfig.kvTimeout(Duration.ofSeconds(30)))
            .build();
    cleanup.deferAfterAll(environment::shutdown);

    String connectionString = couchbase.getConnectionString();
    String seed = connectionString.substring(connectionString.indexOf("://") + 3);
    int portSeparator = seed.lastIndexOf(':');
    seedAddress = seed.substring(0, portSeparator);
    kvPort = Integer.parseInt(seed.substring(portSeparator + 1));
    clusterManagerPort = couchbase.getMappedPort(8091);
    cluster =
        Cluster.connect(
            singleton(
                SeedNode.create(seedAddress, Optional.of(kvPort), Optional.of(clusterManagerPort))),
            ClusterOptions.clusterOptions(couchbase.getUsername(), couchbase.getPassword())
                .environment(environment));
    cleanup.deferAfterAll(cluster::disconnect);

    Bucket bucket = cluster.bucket("test");
    collection = bucket.defaultCollection();

    // Wait 1 minute due to slow startup contributing to flakiness
    bucket.waitUntilReady(Duration.ofMinutes(1));
  }

  @Test
  void testEmitsSpans() throws UnknownHostException {
    try {
      collection.get("id");
    } catch (DocumentNotFoundException ignored) {
      // Expected
    }

    List<AttributeAssertion> dispatchAttributes = new ArrayList<>();
    dispatchAttributes.add(equalTo(maybeStable(DB_SYSTEM), "couchbase"));
    if (emitOldDatabaseSemconv() || EXPERIMENTAL_ATTRIBUTES) {
      dispatchAttributes.add(
          satisfies(stringKey("db.couchbase.local_id"), val -> val.isNotBlank()));
      dispatchAttributes.add(
          satisfies(longKey("db.couchbase.operation_id"), val -> val.isNotNegative()));
      dispatchAttributes.add(
          satisfies(longKey("db.couchbase.server_duration"), val -> val.isNotNegative()));
    }
    if (emitOldDatabaseSemconv()) {
      dispatchAttributes.add(satisfies(stringKey("net.host.name"), val -> val.isNotBlank()));
      dispatchAttributes.add(satisfies(longKey("net.host.port"), val -> val.isPositive()));
      dispatchAttributes.add(satisfies(stringKey("net.peer.name"), val -> val.isNotBlank()));
      dispatchAttributes.add(satisfies(longKey("net.peer.port"), val -> val.isPositive()));
      dispatchAttributes.add(equalTo(stringKey("net.transport"), "IP.TCP"));
    }
    if (emitStableDatabaseSemconv()) {
      dispatchAttributes.add(
          equalTo(
              NETWORK_PEER_ADDRESS, InetAddress.getByName(couchbase.getHost()).getHostAddress()));
      dispatchAttributes.add(equalTo(NETWORK_PEER_PORT, kvPort));
    }

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasKind(INTERNAL) // later version of couchbase gives correct behavior
                      .hasName(spanName())
                      .hasStatus(
                          StatusData.unset()) // later version of couchbase gives correct behavior
                      .hasAttributesSatisfyingExactly(
                          equalTo(maybeStable(DB_SYSTEM), "couchbase"),
                          equalTo(maybeStable(DB_NAME), "test"),
                          equalTo(maybeStable(DB_OPERATION), "get"),
                          equalTo(maybeStable(stringKey("db.couchbase.collection")), "_default"),
                          equalTo(stringKey("db.couchbase.scope"), oldOrExperimental("_default")),
                          equalTo(stringKey("db.couchbase.service"), oldOrExperimental("kv")),
                          equalTo(
                              longKey("db.couchbase.retries"),
                              oldOrExperimental(testLatestDeps() ? 0L : null)),
                          equalTo(SERVER_ADDRESS, serverAddress()),
                          equalTo(SERVER_PORT, null));
                },
                span ->
                    span.hasName("dispatch_to_server")
                        .hasAttributesSatisfyingExactly(dispatchAttributes)));
  }

  private static String serverAddress() {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    String[] endpoints = {seedAddress + ":" + kvPort, seedAddress + ":" + clusterManagerPort};
    Arrays.sort(endpoints);
    return String.join(",", endpoints);
  }

  private static String spanName() {
    return emitStableDatabaseSemconv() ? "get _default" : "get";
  }

  private static <T> T oldOrExperimental(T value) {
    return emitOldDatabaseSemconv() || EXPERIMENTAL_ATTRIBUTES ? value : null;
  }
}
