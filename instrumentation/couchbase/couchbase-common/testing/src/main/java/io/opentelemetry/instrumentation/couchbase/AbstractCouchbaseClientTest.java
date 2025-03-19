/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.cluster.ClusterManager;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.N1qlQueryResult;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractCouchbaseClientTest extends AbstractCouchbaseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static Stream<Arguments> bucketSettings() {
    return Stream.of(
        Arguments.of(named(bucketCouchbase.type().name(), bucketCouchbase)),
        Arguments.of(named(bucketMemcache.type().name(), bucketMemcache)));
  }

  private CouchbaseCluster prepareCluster(BucketSettings bucketSettings) {
    CouchbaseEnvironment environment = envBuilder(bucketSettings).build();
    CouchbaseCluster cluster =
        CouchbaseCluster.create(environment, Collections.singletonList("127.0.0.1"));
    cleanup.deferCleanup(cluster::disconnect);
    cleanup.deferCleanup(environment::shutdown);

    return cluster;
  }

  @ParameterizedTest
  @MethodSource("bucketSettings")
  void hasBucket(BucketSettings bucketSettings) {
    CouchbaseCluster cluster = prepareCluster(bucketSettings);
    ClusterManager manager = cluster.clusterManager(USERNAME, PASSWORD);

    testing.waitForTraces(1);
    testing.clearData();

    boolean hasBucket = manager.hasBucket(bucketSettings.name());
    assertThat(hasBucket).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertCouchbaseSpan(span, "ClusterManager.hasBucket").hasNoParent()));
  }

  @ParameterizedTest
  @MethodSource("bucketSettings")
  void upsertAndGet(BucketSettings bucketSettings) {
    CouchbaseCluster cluster = prepareCluster(bucketSettings);

    // Connect to the bucket and open it
    Bucket bucket = cluster.openBucket(bucketSettings.name(), bucketSettings.password());

    // Create a JSON document and store it with the ID "helloworld"
    JsonObject content = JsonObject.create().put("hello", "world");

    AtomicReference<JsonDocument> inserted = new AtomicReference<>();
    AtomicReference<JsonDocument> found = new AtomicReference<>();

    testing.runWithSpan(
        "someTrace",
        () -> {
          inserted.set(bucket.upsert(JsonDocument.create("helloworld", content)));
          found.set(bucket.get("helloworld"));
        });

    assertThat(found.get()).isEqualTo(inserted.get());
    assertThat(found.get().content().getString("hello")).isEqualTo("world");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Cluster.openBucket")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "Cluster.openBucket"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCouchbaseSpan(span, "Bucket.upsert", bucketSettings.name())
                        .hasParent(trace.getSpan(0)),
                span ->
                    assertCouchbaseSpan(span, "Bucket.get", bucketSettings.name())
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void query() {
    // Only couchbase buckets support queries.
    CouchbaseCluster cluster = prepareCluster(bucketCouchbase);
    Bucket bucket = cluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password());

    // Mock expects this specific query.
    // See com.couchbase.mock.http.query.QueryServer.handleString.
    N1qlQueryResult result = bucket.query(N1qlQuery.simple("SELECT mockrow"));
    assertThat(result.parseSuccess()).isTrue();
    assertThat(result.finalSuccess()).isTrue();
    assertThat(result.rows().next().value().get("row")).isEqualTo("value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Cluster.openBucket")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "Cluster.openBucket"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertCouchbaseSpan(
                            span,
                            "SELECT " + bucketCouchbase.name(),
                            "SELECT",
                            bucketCouchbase.name(),
                            "SELECT mockrow")
                        .hasNoParent()));
  }
}
