/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase.springdata;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.COUCHBASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.ClusterManager;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.couchbase.core.CouchbaseTemplate;

@SuppressWarnings("deprecation")
public abstract class AbstractCouchbaseSpringTemplateTest extends AbstractCouchbaseTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final List<AutoCloseable> cleanup = new ArrayList<>();
  private static CouchbaseTemplate couchbaseTemplate;
  private static CouchbaseTemplate memcacheTemplate;

  @BeforeAll
  void setUp() {
    CouchbaseEnvironment couchbaseEnvironment = envBuilder(bucketCouchbase).build();
    CouchbaseEnvironment memcacheEnvironment = envBuilder(bucketMemcache).build();

    Cluster couchbaseCluster =
        CouchbaseCluster.create(couchbaseEnvironment, Collections.singletonList("127.0.0.1"));
    Cluster memcacheCluster =
        CouchbaseCluster.create(memcacheEnvironment, Collections.singletonList("127.0.0.1"));
    ClusterManager couchbaseManager = couchbaseCluster.clusterManager(USERNAME, PASSWORD);
    ClusterManager memcacheManager = memcacheCluster.clusterManager(USERNAME, PASSWORD);

    Bucket couchbaseBucket =
        couchbaseCluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password());
    Bucket memcacheBucket =
        memcacheCluster.openBucket(bucketMemcache.name(), bucketMemcache.password());

    cleanup.add(couchbaseBucket::close);
    cleanup.add(memcacheBucket::close);
    cleanup.add(couchbaseCluster::disconnect);
    cleanup.add(memcacheCluster::disconnect);
    cleanup.add(couchbaseEnvironment::shutdown);
    cleanup.add(memcacheEnvironment::shutdown);

    testing.runWithSpan(
        "getting info",
        () -> {
          couchbaseTemplate = new CouchbaseTemplate(couchbaseManager.info(), couchbaseBucket);
          memcacheTemplate = new CouchbaseTemplate(memcacheManager.info(), memcacheBucket);
        });
  }

  @AfterAll
  void cleanUp() throws Exception {
    for (AutoCloseable closeable : cleanup) {
      closeable.close();
    }
  }

  private static Stream<Arguments> templates() {
    return Stream.of(
        Arguments.of(named(bucketCouchbase.type().name(), couchbaseTemplate)),
        Arguments.of(named(bucketMemcache.type().name(), memcacheTemplate)));
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
                span ->
                    span.hasName("Bucket.upsert")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_NAME), template.getCouchbaseBucket().name()),
                            equalTo(maybeStable(DB_OPERATION), "Bucket.upsert"),
                            equalTo(NETWORK_TYPE, includesNetworkAttributes() ? "ipv4" : null),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                includesNetworkAttributes() ? "127.0.0.1" : null),
                            satisfies(
                                NETWORK_PEER_PORT,
                                includesNetworkAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.local.address"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.operation_id"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull())),
                span ->
                    span.hasName("Bucket.get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_NAME), template.getCouchbaseBucket().name()),
                            equalTo(maybeStable(DB_OPERATION), "Bucket.get"),
                            equalTo(NETWORK_TYPE, includesNetworkAttributes() ? "ipv4" : null),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                includesNetworkAttributes() ? "127.0.0.1" : null),
                            satisfies(
                                NETWORK_PEER_PORT,
                                includesNetworkAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.local.address"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.operation_id"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()))));
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
                span ->
                    span.hasName("Bucket.upsert")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_NAME), template.getCouchbaseBucket().name()),
                            equalTo(maybeStable(DB_OPERATION), "Bucket.upsert"),
                            equalTo(NETWORK_TYPE, includesNetworkAttributes() ? "ipv4" : null),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                includesNetworkAttributes() ? "127.0.0.1" : null),
                            satisfies(
                                NETWORK_PEER_PORT,
                                includesNetworkAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.local.address"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.operation_id"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull())),
                span ->
                    span.hasName("Bucket.remove")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_NAME), template.getCouchbaseBucket().name()),
                            equalTo(maybeStable(DB_OPERATION), "Bucket.remove"),
                            equalTo(NETWORK_TYPE, includesNetworkAttributes() ? "ipv4" : null),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                includesNetworkAttributes() ? "127.0.0.1" : null),
                            satisfies(
                                NETWORK_PEER_PORT,
                                includesNetworkAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.local.address"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.operation_id"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()))));

    testing.clearData();

    TestDocument result = template.findById("1", TestDocument.class);
    assertThat(result).isNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Bucket.get")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_NAME), template.getCouchbaseBucket().name()),
                            equalTo(maybeStable(DB_OPERATION), "Bucket.get"),
                            equalTo(NETWORK_TYPE, includesNetworkAttributes() ? "ipv4" : null),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                includesNetworkAttributes() ? "127.0.0.1" : null),
                            satisfies(
                                NETWORK_PEER_PORT,
                                includesNetworkAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.local.address"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()),
                            satisfies(
                                AttributeKey.stringKey("couchbase.operation_id"),
                                includesExperimentalAttributes()
                                    ? val -> val.isNotNull()
                                    : val -> val.isNull()))));
  }
}
