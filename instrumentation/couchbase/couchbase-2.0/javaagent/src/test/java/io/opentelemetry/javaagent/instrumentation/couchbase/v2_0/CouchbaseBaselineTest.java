/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.AsyncBucket;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.mock.Bucket.BucketType;
import com.couchbase.mock.BucketConfiguration;
import com.couchbase.mock.CouchbaseMock;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import rx.Observable;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouchbaseBaselineTest {
  private static final String BUCKET_NAME = "baseline";
  private static final String PASSWORD = "test-pass";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private Bucket bucket;
  private AsyncBucket asyncBucket;
  private int carrierPort;

  @BeforeAll
  void setUp() throws Exception {
    CouchbaseMock mock = new CouchbaseMock("127.0.0.1", 0, 1, 1);
    cleanup.deferAfterAll(mock::stop);
    mock.start();
    int httpPort = mock.getHttpPort();

    BucketConfiguration configuration = new BucketConfiguration();
    configuration.name = BUCKET_NAME;
    configuration.password = PASSWORD;
    configuration.type = BucketType.COUCHBASE;
    configuration.numNodes = 1;
    configuration.numReplicas = 0;
    mock.createBucket(configuration);
    carrierPort = mock.getCarrierPort(BUCKET_NAME);

    // Keep this fixture compatible with SDK 2.0.0 and core-io 1.0.0.
    CouchbaseEnvironment environment =
        DefaultCouchbaseEnvironment.builder()
            .bootstrapCarrierDirectPort(carrierPort)
            .bootstrapHttpDirectPort(httpPort)
            .computationPoolSize(1)
            .connectTimeout(SECONDS.toMillis(20))
            .disconnectTimeout(SECONDS.toMillis(20))
            .kvTimeout(SECONDS.toMillis(20))
            .build();
    cleanup.deferAfterAll(() -> shutdownEnvironment(environment));

    CouchbaseCluster cluster = CouchbaseCluster.create(environment, "127.0.0.1");
    cleanup.deferAfterAll(cluster::disconnect);
    bucket = cluster.openBucket(BUCKET_NAME, PASSWORD);
    asyncBucket = bucket.async();
    testing.waitForTraces(1);
  }

  private static void shutdownEnvironment(CouchbaseEnvironment environment) {
    Object shutdownResult = environment.shutdown();
    if (shutdownResult instanceof Observable) {
      shutdownResult = ((Observable<?>) shutdownResult).timeout(20, SECONDS).toBlocking().single();
    }
    assertThat(shutdownResult).isEqualTo(true);
  }

  @Test
  void synchronousKeyValueOperations() {
    testing.runWithSpan(
        "parent",
        () -> {
          JsonDocument inserted =
              bucket.upsert(JsonDocument.create("sync", JsonObject.create().put("hello", "world")));
          JsonDocument found = bucket.get("sync");
          assertThat(found.id()).isEqualTo(inserted.id());
          assertThat(found.cas()).isEqualTo(inserted.cas());
          assertThat(found.content().getString("hello")).isEqualTo("world");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertKeyValueSpan(span, "Bucket.upsert").hasParent(trace.getSpan(0)),
                span -> assertKeyValueSpan(span, "Bucket.get").hasParent(trace.getSpan(0))));
    assertKeyValueDurationMetric();
  }

  @Test
  void asynchronousKeyValueOperations() {
    CompletableFuture<JsonDocument> found = new CompletableFuture<>();
    testing.runWithSpan(
        "parent",
        () ->
            asyncBucket
                .upsert(JsonDocument.create("async", JsonObject.create().put("hello", "world")))
                .subscribe(
                    inserted ->
                        asyncBucket
                            .get(inserted.id())
                            .subscribe(found::complete, found::completeExceptionally),
                    found::completeExceptionally));

    assertThat(found)
        .succeedsWithin(Duration.ofSeconds(20))
        .extracting(result -> result.content().getString("hello"))
        .isEqualTo("world");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertKeyValueSpan(span, "Bucket.upsert").hasParent(trace.getSpan(0)),
                span -> assertKeyValueSpan(span, "Bucket.get").hasParent(trace.getSpan(1))));
    assertKeyValueDurationMetric();
  }

  private SpanDataAssert assertKeyValueSpan(SpanDataAssert span, String operation) {
    // Pre-1.6 core has neither the local socket address nor the operation id, even in experimental
    // mode.
    return span.hasName(operation + " " + BUCKET_NAME)
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(DB_SYSTEM_NAME, "couchbase"),
            equalTo(DB_NAMESPACE, BUCKET_NAME),
            equalTo(DB_OPERATION_NAME, operation),
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_PEER_PORT, carrierPort));
  }

  private void assertKeyValueDurationMetric() {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.couchbase-2.0",
        metric ->
            metric
                .hasName("db.client.operation.duration")
                .hasUnit("s")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point -> assertKeyValueDurationPoint(point, "Bucket.upsert"),
                            point -> assertKeyValueDurationPoint(point, "Bucket.get"))));
  }

  private void assertKeyValueDurationPoint(HistogramPointAssert point, String operation) {
    point
        .hasCount(1)
        .hasAttributesSatisfyingExactly(
            equalTo(DB_SYSTEM_NAME, "couchbase"),
            equalTo(DB_NAMESPACE, BUCKET_NAME),
            equalTo(DB_OPERATION_NAME, operation),
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_PEER_PORT, carrierPort));
  }
}
