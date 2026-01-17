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
import io.opentelemetry.instrumentation.couchbase.springdata.AbstractCouchbaseSpringTemplateTest;
import io.opentelemetry.instrumentation.couchbase.springdata.TestDocument;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_6.Couchbase26Util;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.couchbase.core.CouchbaseTemplate;

@SuppressWarnings("deprecation")
class CouchbaseSpringTemplate26Test extends AbstractCouchbaseSpringTemplateTest {

  private static final String EXPERIMENTAL_FLAG =
      "otel.instrumentation.couchbase.experimental-span-attributes";

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return Couchbase26Util.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @ParameterizedTest
  @MethodSource("templates")
  void write(CouchbaseTemplate template) {
    TestDocument document = new TestDocument();
    TestDocument result =
        testing.runWithSpan(
            "someTrace",
            () -> {
              template.save(document);
              return template.findById("1", TestDocument.class);
            });

    assertThat(result).isNotNull();

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
                                .containsEntry(
                                    maybeStable(DB_NAME), template.getCouchbaseBucket().name())
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
                                .containsEntry(
                                    maybeStable(DB_NAME), template.getCouchbaseBucket().name())
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

  @ParameterizedTest
  @MethodSource("templates")
  void remove(CouchbaseTemplate template) {
    TestDocument document = new TestDocument();
    testing.runWithSpan(
        "someTrace",
        () -> {
          template.save(document);
          template.remove(document);
        });

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
                                .containsEntry(
                                    maybeStable(DB_NAME), template.getCouchbaseBucket().name())
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
                                .containsEntry(
                                    maybeStable(DB_NAME), template.getCouchbaseBucket().name())
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
                }));

    testing.clearData();

    TestDocument result = template.findById("1", TestDocument.class);
    assertThat(result).isNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("Bucket.get")
                      .hasKind(SpanKind.CLIENT)
                      .hasNoParent()
                      .hasAttributesSatisfying(
                          attrs -> {
                            assertThat(attrs)
                                .containsEntry(maybeStable(DB_SYSTEM), COUCHBASE)
                                .containsEntry(
                                    maybeStable(DB_NAME), template.getCouchbaseBucket().name())
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
}
