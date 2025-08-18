/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.cluster.ClusterManager;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseClientTest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;
import org.junit.jupiter.api.Test;

class CouchbaseClient26Test extends AbstractCouchbaseClientTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return Couchbase26Util.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected List<AttributeAssertion> couchbaseAttributes() {
    return Couchbase26Util.couchbaseAttributes();
  }

  @Test
  void hasDurationMetric() {
    CouchbaseCluster cluster = prepareCluster(bucketCouchbase);
    ClusterManager manager = cluster.clusterManager(USERNAME, PASSWORD);

    testing.waitForTraces(1);
    testing.clearData();

    boolean hasBucket = manager.hasBucket(bucketCouchbase.name());
    assertThat(hasBucket).isTrue();
  }
}
