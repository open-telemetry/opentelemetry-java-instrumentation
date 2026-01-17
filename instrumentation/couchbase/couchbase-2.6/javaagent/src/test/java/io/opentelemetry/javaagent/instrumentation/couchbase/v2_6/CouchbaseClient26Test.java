/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.COUCHBASE;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.cluster.ClusterManager;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.N1qlQueryResult;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseClientTest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class CouchbaseClient26Test extends AbstractCouchbaseClientTest {

  private static final String EXPERIMENTAL_FLAG =
      "otel.instrumentation.couchbase.experimental-span-attributes";

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return Couchbase26Util.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @ParameterizedTest
  @MethodSource("bucketSettings")
  @Override
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
                span ->
                    span.hasName("ClusterManager.hasBucket")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "ClusterManager.hasBucket"),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()))));
  }

  @ParameterizedTest
  @MethodSource("bucketSettings")
  @Override
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
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "Cluster.openBucket"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> {
                  span.hasName("Bucket.upsert")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(
                                    maybeStable(DB_NAME), bucketSettings.name())
                                .containsEntry(
                                    maybeStable(DB_OPERATION), "Bucket.upsert")
                                .containsEntry(NETWORK_TYPE, "ipv4")
                                .containsEntry(NETWORK_PEER_ADDRESS, "127.0.0.1")
                                .containsKey(NETWORK_PEER_PORT);
                            if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
                              assertThat(attrs)
                                  .containsKey(AttributeKey.stringKey("couchbase.local.address"))
                                  .containsKey(AttributeKey.stringKey("couchbase.operation_id"));
                            }
                          });
                },
                span -> {
                  span.hasName("Bucket.get")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(
                                    maybeStable(DB_NAME), bucketSettings.name())
                                .containsEntry(maybeStable(DB_OPERATION), "Bucket.get")
                                .containsEntry(NETWORK_TYPE, "ipv4")
                                .containsEntry(NETWORK_PEER_ADDRESS, "127.0.0.1")
                                .containsKey(NETWORK_PEER_PORT);
                            if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
                              assertThat(attrs)
                                  .containsKey(AttributeKey.stringKey("couchbase.local.address"))
                                  .containsKey(AttributeKey.stringKey("couchbase.operation_id"));
                            }
                          });
                }));
  }

  @Test
  @Override
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
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "Cluster.openBucket"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("SELECT " + bucketCouchbase.name())
                      .hasKind(SpanKind.CLIENT)
                      .hasNoParent()
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(
                                    maybeStable(DB_NAME), bucketCouchbase.name())
                                .containsEntry(maybeStable(DB_OPERATION), "SELECT")
                                .containsEntry(NETWORK_TYPE, "ipv4")
                                .containsEntry(NETWORK_PEER_ADDRESS, "127.0.0.1")
                                .containsKey(NETWORK_PEER_PORT);
                            assertThat(
                                    attrs.get(maybeStable(DB_STATEMENT)).toString())
                                .startsWith("SELECT mockrow");
                            if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
                              assertThat(attrs)
                                  .containsKey(AttributeKey.stringKey("couchbase.operation_id"));
                            }
                          });
                }));
  }

  @Test
  void hasDurationMetric() {
    CouchbaseCluster cluster = prepareCluster(bucketCouchbase);
    ClusterManager manager = cluster.clusterManager(USERNAME, PASSWORD);

    testing.waitForTraces(1);
    testing.clearData();

    boolean hasBucket = manager.hasBucket(bucketCouchbase.name());
    assertThat(hasBucket).isTrue();

    assertDurationMetric(
        testing,
        // The metric is generated by couchbase-2.0 instrumentation
        "io.opentelemetry.couchbase-2.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
  }
}
