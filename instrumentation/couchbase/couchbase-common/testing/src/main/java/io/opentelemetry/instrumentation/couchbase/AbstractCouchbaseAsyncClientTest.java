/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.couchbase.client.java.CouchbaseAsyncCluster;
import com.couchbase.client.java.cluster.AsyncClusterManager;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.query.AsyncN1qlQueryResult;
import com.couchbase.client.java.query.N1qlQuery;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractCouchbaseAsyncClientTest extends AbstractCouchbaseTest {

  private static final int TIMEOUT_SECONDS = 10;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static Stream<Arguments> bucketSettings() {
    return Stream.of(
        Arguments.of(named(bucketCouchbase.type().name(), bucketCouchbase)),
        Arguments.of(named(bucketMemcache.type().name(), bucketMemcache)));
  }

  private CouchbaseAsyncCluster prepareCluster(BucketSettings bucketSettings) {
    CouchbaseEnvironment environment = envBuilder(bucketSettings).build();
    CouchbaseAsyncCluster cluster =
        CouchbaseAsyncCluster.create(environment, Collections.singletonList("127.0.0.1"));
    cleanup.deferCleanup(
        () -> cluster.disconnect().timeout(10, TimeUnit.SECONDS).toBlocking().single());
    cleanup.deferCleanup(environment::shutdown);

    return cluster;
  }

  @ParameterizedTest
  @MethodSource("bucketSettings")
  void hasBucket(BucketSettings bucketSettings)
      throws ExecutionException, InterruptedException, TimeoutException {
    CouchbaseAsyncCluster cluster = prepareCluster(bucketSettings);
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
                            equalTo(
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.COUCHBASE),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "Cluster.openBucket")),
                span ->
                    assertCouchbaseSpan(span, "ClusterManager.hasBucket")
                        .hasParent(trace.getSpan(0))));
  }

  @ParameterizedTest
  @MethodSource("bucketSettings")
  void upsert(BucketSettings bucketSettings)
      throws ExecutionException, InterruptedException, TimeoutException {
    CouchbaseAsyncCluster cluster = prepareCluster(bucketSettings);

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
                            equalTo(
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.COUCHBASE),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "Cluster.openBucket")),
                span ->
                    assertCouchbaseSpan(span, "Bucket.upsert", bucketSettings.name())
                        .hasParent(trace.getSpan(1))));
  }

  @ParameterizedTest
  @MethodSource("bucketSettings")
  void upsertAndGet(BucketSettings bucketSettings)
      throws ExecutionException, InterruptedException, TimeoutException {
    CouchbaseAsyncCluster cluster = prepareCluster(bucketSettings);

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
                            equalTo(
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.COUCHBASE),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "Cluster.openBucket")),
                span ->
                    assertCouchbaseSpan(span, "Bucket.upsert", bucketSettings.name())
                        .hasParent(trace.getSpan(1)),
                span ->
                    assertCouchbaseSpan(span, "Bucket.get", bucketSettings.name())
                        .hasParent(trace.getSpan(2))));
  }

  @Test
  void query() throws ExecutionException, InterruptedException, TimeoutException {
    // Only couchbase buckets support queries.
    CouchbaseAsyncCluster cluster = prepareCluster(bucketCouchbase);

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
                            equalTo(
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.COUCHBASE),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "Cluster.openBucket")),
                span ->
                    assertCouchbaseSpan(
                            span,
                            "SELECT " + bucketCouchbase.name(),
                            "SELECT",
                            bucketCouchbase.name(),
                            "SELECT mockrow")
                        .hasParent(trace.getSpan(1))));
  }
}
