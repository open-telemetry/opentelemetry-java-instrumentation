/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.cluster.ClusterManager;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseClientTest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;
import org.junit.jupiter.api.Test;

class CouchbaseClientTest extends AbstractCouchbaseClientTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return CouchbaseUtil.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected List<AttributeAssertion> couchbaseAttributes() {
    return CouchbaseUtil.couchbaseAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseQueryAttributes() {
    return CouchbaseUtil.couchbaseQueryAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseClusterManagerAttributes() {
    return CouchbaseUtil.couchbaseClusterManagerAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseN1qlAttributes() {
    return CouchbaseUtil.couchbaseN1qlAttributes();
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
        testing, "io.opentelemetry.couchbase-2.0", DB_SYSTEM_NAME, DB_OPERATION_NAME);
  }
}
