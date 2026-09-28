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
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;

import com.couchbase.client.core.CoreProtostellar;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.core.protostellar.ProtostellarRequest;
import com.couchbase.client.core.retry.RetryStrategy;
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.core.util.ConnectionString;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.couchbase.CouchbaseService;

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
                span -> {
                  assertOperationSpan(
                      span,
                      "get",
                      "id",
                      testLatestDeps() ? StatusData.error() : StatusData.unset());
                  span.hasNoParent();
                },
                span ->
                    span.hasName("dispatch_to_server")
                        .hasKind(v3Preview() ? INTERNAL : (testLatestDeps() ? CLIENT : INTERNAL))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(dispatchAttributes));
          } else {
            trace.hasSpansSatisfyingExactly(
                span -> {
                  assertOperationSpan(
                      span,
                      "get",
                      "id",
                      testLatestDeps() ? StatusData.error() : StatusData.unset());
                  span.hasNoParent();
                });
          }
        });
  }

  @Test
  void asyncWriteEmitsLifecycleSpans() {
    collection.async().upsert("async-id", JsonObject.create().put("value", "test")).join();

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace -> {
          if (emitSdkDetailSpans()) {
            trace.hasSpansSatisfyingExactly(
                span -> {
                  assertOperationSpan(span, "upsert", "async-id", StatusData.unset());
                  span.hasNoParent();
                },
                span ->
                    span.hasName("request_encoding")
                        .hasKind(v3Preview() ? INTERNAL : (testLatestDeps() ? CLIENT : INTERNAL))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("dispatch_to_server")
                        .hasKind(v3Preview() ? INTERNAL : (testLatestDeps() ? CLIENT : INTERNAL))
                        .hasParent(trace.getSpan(0)));
          } else {
            trace.hasSpansSatisfyingExactly(
                span -> {
                  assertOperationSpan(span, "upsert", "async-id", StatusData.unset());
                  span.hasNoParent();
                });
          }
        });
  }

  @ParameterizedTest
  @MethodSource("protostellarTargets")
  void testEmitsProtostellarTarget(String portSuffix, Long expectedPort) {
    assumeTrue(testLatestDeps());
    Cluster protostellar =
        Cluster.connect(
            "couchbase2://" + seedAddress + portSuffix,
            couchbase.getUsername(),
            couchbase.getPassword());
    cleanup.deferCleanup(protostellar::disconnect);

    CoreProtostellar core = (CoreProtostellar) protostellar.async().couchbaseOps();
    RequestSpan requestSpan =
        cluster.core().coreResources().requestTracer().requestSpan("get", null);
    ProtostellarRequest<Object> protostellarRequest =
        new ProtostellarRequest<>(
            null,
            core,
            ServiceType.KV,
            "get",
            requestSpan,
            Duration.ofSeconds(1),
            true,
            mock(RetryStrategy.class),
            emptyMap(),
            0L,
            null);
    protostellarRequest.raisedResponseToUser(null);

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CLIENT)
                        .hasName(
                            emitStableDatabaseSemconv()
                                ? "get " + seedAddress + (expectedPort == null ? "" : portSuffix)
                                : "get")
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "couchbase"),
                            equalTo(longKey("db.couchbase.retries"), experimental(0L)),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? seedAddress : null),
                            equalTo(
                                SERVER_PORT, emitStableDatabaseSemconv() ? expectedPort : null))));
  }

  private static Stream<Arguments> protostellarTargets() {
    return Stream.of(
        argumentSet("implicit default port", "", null),
        argumentSet("explicit default port", ":18098", null),
        argumentSet("non-default port", ":18099", 18099L));
  }

  private static void assertOperationSpan(
      SpanDataAssert span, String operation, String documentId, StatusData status) {
    span.hasKind(v3Preview() || testLatestDeps() ? CLIENT : INTERNAL)
        .hasName(emitStableDatabaseSemconv() ? operation + " _default" : operation)
        .hasStatus(status)
        .hasAttributesSatisfyingExactly(
            equalTo(maybeStable(DB_SYSTEM), "couchbase"),
            equalTo(maybeStable(DB_NAME), "test"),
            equalTo(maybeStable(DB_OPERATION), operation),
            equalTo(maybeStable(stringKey("db.couchbase.collection")), "_default"),
            equalTo(stringKey("db.couchbase.document_id"), experimental(documentId)),
            equalTo(stringKey("db.couchbase.scope"), experimental("_default")),
            equalTo(longKey("db.couchbase.retries"), experimental(0L)),
            equalTo(stringKey("db.couchbase.service"), experimental("kv")),
            equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? seedAddress : null),
            equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? (long) seedPort : null));
  }

  private static boolean emitSdkDetailSpans() {
    return !v3Preview() || EXPERIMENTAL_TELEMETRY || LEGACY_EXPERIMENTAL_ATTRIBUTES;
  }

  private static boolean emitExperimentalAttributes() {
    return emitOldDatabaseSemconv() || EXPERIMENTAL_TELEMETRY || LEGACY_EXPERIMENTAL_ATTRIBUTES;
  }

  private static <T> T experimental(T value) {
    return emitExperimentalAttributes() ? value : null;
  }
}
