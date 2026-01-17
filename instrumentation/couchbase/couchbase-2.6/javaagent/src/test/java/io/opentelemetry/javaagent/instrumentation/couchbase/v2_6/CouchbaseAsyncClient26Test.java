/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.COUCHBASE;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.CouchbaseAsyncCluster;
import com.couchbase.client.java.cluster.AsyncClusterManager;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.client.java.query.AsyncN1qlQueryResult;
import com.couchbase.client.java.query.N1qlQuery;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseAsyncClientTest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class CouchbaseAsyncClient26Test extends AbstractCouchbaseAsyncClientTest {

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
  void hasBucket(BucketSettings bucketSettings)
      throws ExecutionException, InterruptedException, TimeoutException {
    CouchbaseAsyncCluster cluster = getCluster(bucketSettings);
    AsyncClusterManager manager = cluster.clusterManager(USERNAME, PASSWORD).toBlocking().single();

    testing.waitForTraces(1);
    testing.clearData();

    CompletableFuture<Boolean> hasBucket = new CompletableFuture<>();
    cluster
        .openBucket(bucketSettings.name(), bucketSettings.password())
        .subscribe(
            bucket -> manager.hasBucket(bucketSettings.name()).subscribe(hasBucket::complete));

    assertThat(hasBucket.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Cluster.openBucket")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "Cluster.openBucket")),
                span ->
                    span.hasName("ClusterManager.hasBucket")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
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
  void upsert(BucketSettings bucketSettings)
      throws ExecutionException, InterruptedException, TimeoutException {
    CouchbaseAsyncCluster cluster = getCluster(bucketSettings);

    JsonObject content = JsonObject.create().put("hello", "world");
    CompletableFuture<JsonDocument> inserted = new CompletableFuture<>();
    testing.runWithSpan(
        "someTrace",
        () -> {
          cluster
              .openBucket(bucketSettings.name(), bucketSettings.password())
              .subscribe(
                  bucket ->
                      bucket
                          .upsert(JsonDocument.create("helloworld", content))
                          .subscribe(inserted::complete));
        });

    assertThat(inserted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).content().getString("hello"))
        .isEqualTo("world");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Cluster.openBucket")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "Cluster.openBucket")),
                span -> {
                  span.hasName("Bucket.upsert")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(1))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketSettings.name())
                                .containsEntry(maybeStable(DB_OPERATION), "Bucket.upsert")
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

  @ParameterizedTest
  @MethodSource("bucketSettings")
  @Override
  void upsertAndGet(BucketSettings bucketSettings)
      throws ExecutionException, InterruptedException, TimeoutException {
    CouchbaseAsyncCluster cluster = getCluster(bucketSettings);

    JsonObject content = JsonObject.create().put("hello", "world");
    CompletableFuture<JsonDocument> inserted = new CompletableFuture<>();
    CompletableFuture<JsonDocument> found = new CompletableFuture<>();
    testing.runWithSpan(
        "someTrace",
        () -> {
          cluster
              .openBucket(bucketSettings.name(), bucketSettings.password())
              .subscribe(
                  bucket ->
                      bucket
                          .upsert(JsonDocument.create("helloworld", content))
                          .subscribe(
                              result -> {
                                inserted.complete(result);
                                bucket.get("helloworld").subscribe(found::complete);
                              }));
        });

    JsonDocument insertedResult = inserted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    JsonDocument foundResult = found.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertThat(foundResult).isEqualTo(insertedResult);
    assertThat(foundResult.content().getString("hello")).isEqualTo("world");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Cluster.openBucket")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "Cluster.openBucket")),
                span -> {
                  span.hasName("Bucket.upsert")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(1))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketSettings.name())
                                .containsEntry(maybeStable(DB_OPERATION), "Bucket.upsert")
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
                      .hasParent(trace.getSpan(2))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketSettings.name())
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
  void query() throws ExecutionException, InterruptedException, TimeoutException {
    // Only couchbase buckets support queries.
    CouchbaseAsyncCluster cluster = getCluster(bucketCouchbase);

    CompletableFuture<JsonObject> queryResult = new CompletableFuture<>();
    // Mock expects this specific query.
    // See com.couchbase.mock.http.query.QueryServer.handleString.
    testing.runWithSpan(
        "someTrace",
        () -> {
          cluster
              .openBucket(bucketCouchbase.name(), bucketCouchbase.password())
              .subscribe(
                  bucket ->
                      bucket
                          .query(N1qlQuery.simple("SELECT mockrow"))
                          .flatMap(AsyncN1qlQueryResult::rows)
                          .single()
                          .subscribe(row -> queryResult.complete(row.value())));
        });

    assertThat(queryResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get("row")).isEqualTo("value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Cluster.openBucket")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "Cluster.openBucket")),
                span -> {
                  span.hasName("SELECT " + bucketCouchbase.name())
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(1))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
                                .containsEntry(maybeStable(DB_OPERATION), "SELECT")
                                .containsEntry(NETWORK_TYPE, "ipv4")
                                .containsEntry(NETWORK_PEER_ADDRESS, "127.0.0.1")
                                .containsKey(NETWORK_PEER_PORT);
                            assertThat(attrs.get(maybeStable(DB_STATEMENT)).toString())
                                .startsWith("SELECT mockrow");
                            if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
                              assertThat(attrs)
                                  .containsKey(AttributeKey.stringKey("couchbase.operation_id"));
                            }
                          });
                }));
  }
}
