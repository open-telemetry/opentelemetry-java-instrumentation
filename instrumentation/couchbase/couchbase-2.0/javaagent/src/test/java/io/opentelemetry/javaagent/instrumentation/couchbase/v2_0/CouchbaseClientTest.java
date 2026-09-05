/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.COUCHBASE;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.cluster.ClusterManager;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseClientTest;
import org.junit.jupiter.api.Test;

class CouchbaseClientTest extends AbstractCouchbaseClientTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return CouchbaseUtil.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected boolean includesNetworkAttributes() {
    return emitStableDatabaseSemconv();
  }

  @Override
  protected boolean includesLocalAddressAttribute() {
    // The core-io versions before 1.6.0 have no localSocket field to capture it from.
    return false;
  }

  @Override
  protected boolean includesOperationIdAttribute() {
    // The core-io versions before 1.6.0 have no CouchbaseRequest.operationId() to correlate with.
    return false;
  }

  @Override
  protected boolean includesOldServerAddressAttribute() {
    // This module never resolves a node string to pair with the actual peer address.
    return false;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void hasExpectedSpanAndDurationMetric() {
    CouchbaseCluster cluster = getCluster(bucketCouchbase);
    ClusterManager manager = cluster.clusterManager(USERNAME, PASSWORD);

    testing.waitForTraces(1);
    testing.clearData();

    boolean hasBucket = manager.hasBucket(bucketCouchbase.name());
    assertThat(hasBucket).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("ClusterManager.hasBucket"))
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), COUCHBASE),
                            equalTo(maybeStable(DB_OPERATION), "ClusterManager.hasBucket"),
                            equalTo(NETWORK_TYPE, networkType()),
                            equalTo(NETWORK_PEER_ADDRESS, networkPeerAddress()),
                            satisfies(NETWORK_PEER_PORT, networkPeerPort()),
                            satisfies(SERVER_ADDRESS, serverAddress()),
                            satisfies(SERVER_PORT, serverPort()),
                            satisfies(
                                stringKey("couchbase.local.address"), localAddressAttribute()))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.couchbase-2.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS);
  }
}
