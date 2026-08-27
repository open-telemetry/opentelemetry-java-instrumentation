/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

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

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
class CouchbaseClient32Test {
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.couchbase.experimental-span-attributes");

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("couchbase-container");

  private static CouchbaseContainer couchbase;
  private static String connectionString;
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
            .withStartupTimeout(Duration.ofMinutes(2));
    couchbase.start();
    cleanup.deferAfterAll(couchbase::stop);
    connectionString = couchbase.getConnectionString();

    cluster = Cluster.connect(connectionString, couchbase.getUsername(), couchbase.getPassword());
    cleanup.deferAfterAll(cluster::disconnect);
    Bucket bucket = cluster.bucket("test");
    collection = bucket.defaultCollection();
    bucket.waitUntilReady(Duration.ofSeconds(30));
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
    dispatchAttributes.add(equalTo(maybeStable(DB_NAME), "test"));
    dispatchAttributes.add(equalTo(maybeStable(stringKey("db.couchbase.collection")), "_default"));
    if (emitOldDatabaseSemconv() || EXPERIMENTAL_ATTRIBUTES) {
      dispatchAttributes.add(equalTo(stringKey("db.couchbase.document_id"), "id"));
      dispatchAttributes.add(
          satisfies(stringKey("db.couchbase.local_id"), val -> val.isNotBlank()));
      dispatchAttributes.add(
          satisfies(longKey("db.couchbase.operation_id"), val -> val.isNotNegative()));
      dispatchAttributes.add(equalTo(stringKey("db.couchbase.scope"), "_default"));
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
      dispatchAttributes.add(equalTo(NETWORK_PEER_PORT, serverPort()));
    }

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasKind(INTERNAL) // later version of couchbase gives correct behavior
                      .hasName(spanName());
                  if (testLatestDeps()) {
                    span.hasStatus(StatusData.error());
                  }
                  span.hasAttributesSatisfyingExactly(
                      equalTo(maybeStable(DB_SYSTEM), "couchbase"),
                      equalTo(maybeStable(DB_NAME), "test"),
                      equalTo(maybeStable(DB_OPERATION), "get"),
                      equalTo(maybeStable(stringKey("db.couchbase.collection")), "_default"),
                      equalTo(stringKey("db.couchbase.document_id"), oldOrExperimental("id")),
                      equalTo(stringKey("db.couchbase.scope"), oldOrExperimental("_default")),
                      equalTo(longKey("db.couchbase.retries"), oldOrExperimental(0L)),
                      equalTo(stringKey("db.couchbase.service"), oldOrExperimental("kv")),
                      equalTo(SERVER_ADDRESS, serverAddress()),
                      equalTo(SERVER_PORT, serverPort()));
                },
                span ->
                    span.hasName("dispatch_to_server")
                        .hasAttributesSatisfyingExactly(dispatchAttributes)));
  }

  private static String serverAddress() {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    String seed = connectionString.substring(connectionString.indexOf("://") + 3);
    return seed.substring(0, seed.lastIndexOf(':'));
  }

  private static Long serverPort() {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    return Long.valueOf(connectionString.substring(connectionString.lastIndexOf(':') + 1));
  }

  private static String spanName() {
    return emitStableDatabaseSemconv() ? "get _default" : "get";
  }

  private static <T> T oldOrExperimental(T value) {
    return emitOldDatabaseSemconv() || EXPERIMENTAL_ATTRIBUTES ? value : null;
  }
}
