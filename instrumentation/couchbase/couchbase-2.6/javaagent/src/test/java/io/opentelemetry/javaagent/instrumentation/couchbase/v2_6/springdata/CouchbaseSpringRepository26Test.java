/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6.springdata;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.COUCHBASE;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.couchbase.springdata.AbstractCouchbaseSpringRepositoryTest;
import io.opentelemetry.instrumentation.couchbase.springdata.TestDocument;
import io.opentelemetry.instrumentation.couchbase.springdata.TestRepository;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_6.Couchbase26Util;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class CouchbaseSpringRepository26Test extends AbstractCouchbaseSpringRepositoryTest {

  private static final String EXPERIMENTAL_FLAG =
      "otel.instrumentation.couchbase.experimental-span-attributes";

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return Couchbase26Util.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected TestDocument findById(TestRepository repository, String id) {
    return repository.findById(id).get();
  }

  @Override
  protected void deleteById(TestRepository repository, String id) {
    repository.deleteById(id);
  }

  @Test
  void emptyRepo() {
    Iterable<TestDocument> result = repository.findAll();

    assertThat(result.iterator().hasNext()).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName(bucketCouchbase.name())
                      .hasKind(SpanKind.CLIENT)
                      .hasNoParent()
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
                                .containsEntry(NETWORK_TYPE, "ipv4")
                                .containsEntry(NETWORK_PEER_ADDRESS, "127.0.0.1")
                                .containsKey(NETWORK_PEER_PORT);
                            if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
                              assertThat(attrs)
                                  .containsKey(AttributeKey.stringKey("couchbase.local.address"));
                            }
                          });
                }));
  }

  @Test
  void save() {
    TestDocument document = new TestDocument();
    TestDocument result = repository.save(document);
    cleanup.deferCleanup(this::cleanUpTest);

    assertThat(result).isEqualTo(document);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("Bucket.upsert")
                      .hasKind(SpanKind.CLIENT)
                      .hasNoParent()
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
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

  @Test
  void saveAndRetrieve() {
    TestDocument document = new TestDocument();
    TestDocument result =
        testing.runWithSpan(
            "someTrace",
            () -> {
              repository.save(document);
              return findById(repository, "1");
            });
    cleanup.deferCleanup(this::cleanUpTest);

    assertThat(result).isEqualTo(document);

    testing.waitAndAssertTraces(
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
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
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
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
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
  void saveAndUpdate() {
    TestDocument document = new TestDocument();
    testing.runWithSpan(
        "someTrace",
        () -> {
          repository.save(document);
          document.setData("other data");
          repository.save(document);
        });
    cleanup.deferCleanup(this::cleanUpTest);

    testing.waitAndAssertTraces(
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
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
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
                  span.hasName("Bucket.upsert")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
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

  @Test
  void saveAndDelete() {
    TestDocument document = new TestDocument();
    boolean found =
        testing.runWithSpan(
            "someTrace",
            () -> {
              repository.save(document);
              deleteById(repository, "1");
              return repository.findAll().iterator().hasNext();
            });

    assertThat(found).isFalse();

    testing.waitAndAssertTraces(
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
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
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
                  span.hasName("Bucket.remove")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
                                .containsEntry(maybeStable(DB_OPERATION), "Bucket.remove")
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
                  span.hasName(bucketCouchbase.name())
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(maybeStable(DB_NAME), bucketCouchbase.name())
                                .containsEntry(NETWORK_TYPE, "ipv4")
                                .containsEntry(NETWORK_PEER_ADDRESS, "127.0.0.1")
                                .containsKey(NETWORK_PEER_PORT);
                            if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
                              assertThat(attrs)
                                  .containsKey(AttributeKey.stringKey("couchbase.local.address"));
                            }
                          });
                }));
  }
}
