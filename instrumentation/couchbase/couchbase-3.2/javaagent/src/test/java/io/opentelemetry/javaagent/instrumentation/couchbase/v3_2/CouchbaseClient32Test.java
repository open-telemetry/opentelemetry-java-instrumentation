/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
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
import static io.opentelemetry.semconv.incubating.NetIncubatingAttributes.NET_HOST_NAME;
import static io.opentelemetry.semconv.incubating.NetIncubatingAttributes.NET_HOST_PORT;
import static io.opentelemetry.semconv.incubating.NetIncubatingAttributes.NET_PEER_NAME;
import static io.opentelemetry.semconv.incubating.NetIncubatingAttributes.NET_PEER_PORT;
import static io.opentelemetry.semconv.incubating.NetIncubatingAttributes.NET_TRANSPORT;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.core.util.ConnectionString;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
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
  private static final boolean LEGACY_EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.couchbase.experimental-span-attributes");
  private static final boolean EXPERIMENTAL_TELEMETRY =
      Boolean.getBoolean("otel.instrumentation.couchbase.emit-experimental-telemetry");

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("couchbase-container");

  private static CouchbaseContainer couchbase;
  private static String seedAddress;
  private static int seedPort;
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
    String connectionString = couchbase.getConnectionString();
    ConnectionString.UnresolvedSocket seed =
        ConnectionString.create(connectionString).hosts().get(0);
    seedAddress = seed.hostname();
    seedPort = seed.port();

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
    if (emitExperimentalAttributes()) {
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
      dispatchAttributes.add(satisfies(NET_HOST_NAME, val -> val.isNotBlank()));
      dispatchAttributes.add(satisfies(NET_HOST_PORT, val -> val.isPositive()));
      dispatchAttributes.add(satisfies(NET_PEER_NAME, val -> val.isNotBlank()));
      dispatchAttributes.add(satisfies(NET_PEER_PORT, val -> val.isPositive()));
      dispatchAttributes.add(equalTo(NET_TRANSPORT, "IP.TCP"));
    }
    if (emitStableDatabaseSemconv()) {
      dispatchAttributes.add(
          equalTo(
              NETWORK_PEER_ADDRESS, InetAddress.getByName(couchbase.getHost()).getHostAddress()));
      dispatchAttributes.add(equalTo(NETWORK_PEER_PORT, seedPort));
    }

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace -> {
          if (emitSdkDetailSpans()) {
            trace.hasSpansSatisfyingExactly(
                CouchbaseClient32Test::assertGetSpan,
                span ->
                    span.hasName("dispatch_to_server")
                        .hasKind(v3Preview() ? INTERNAL : (testLatestDeps() ? CLIENT : INTERNAL))
                        .hasAttributesSatisfyingExactly(dispatchAttributes));
          } else {
            trace.hasSpansSatisfyingExactly(CouchbaseClient32Test::assertGetSpan);
          }
        });
  }

  private static void assertGetSpan(SpanDataAssert span) {
    span.hasKind(v3Preview() || testLatestDeps() ? CLIENT : INTERNAL)
        .hasName(emitStableDatabaseSemconv() ? "get _default" : "get");
    if (testLatestDeps()) {
      span.hasStatus(StatusData.error());
    }
    span.hasAttributesSatisfyingExactly(
        equalTo(maybeStable(DB_SYSTEM), "couchbase"),
        equalTo(maybeStable(DB_NAME), "test"),
        equalTo(maybeStable(DB_OPERATION), "get"),
        equalTo(maybeStable(stringKey("db.couchbase.collection")), "_default"),
        equalTo(stringKey("db.couchbase.document_id"), experimental("id")),
        equalTo(stringKey("db.couchbase.scope"), experimental("_default")),
        equalTo(longKey("db.couchbase.retries"), experimental(0L)),
        equalTo(stringKey("db.couchbase.service"), experimental("kv")),
        equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? seedAddress : null),
        equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? (long) seedPort : null));
  }

  private static boolean emitSdkDetailSpans() {
    return !v3Preview() || EXPERIMENTAL_TELEMETRY;
  }

  private static boolean emitExperimentalAttributes() {
    return emitOldDatabaseSemconv()
        || (v3Preview() ? EXPERIMENTAL_TELEMETRY : LEGACY_EXPERIMENTAL_ATTRIBUTES);
  }

  private static <T> T experimental(T value) {
    return emitExperimentalAttributes() ? value : null;
  }
}
