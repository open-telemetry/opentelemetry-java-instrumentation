/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.ClusterManager;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractCouchbaseSelectorTest extends AbstractCouchbaseTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  protected void selectorsControlCoreAndNetworkEnrichment() {
    CouchbaseEnvironment environment = envBuilder(bucketCouchbase).build();
    CouchbaseCluster cluster = CouchbaseCluster.create(environment, singletonList("127.0.0.1"));
    try {
      ClusterManager manager = cluster.clusterManager(USERNAME, PASSWORD);
      if (Boolean.getBoolean("couchbase.test.core-enabled")) {
        testing.waitForTraces(1);
      }
      testing.clearData();
      testing.runWithSpan(
          "parent", () -> assertThat(manager.hasBucket(bucketCouchbase.name())).isTrue());

      if (Boolean.getBoolean("couchbase.test.core-enabled")) {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent"),
                    span ->
                        span.hasName("ClusterManager.hasBucket 127.0.0.1")
                            .hasAttributesSatisfying(
                                equalTo(
                                    NETWORK_PEER_ADDRESS,
                                    Boolean.getBoolean("couchbase.test.network-enabled")
                                        ? "127.0.0.1"
                                        : null))));
      } else {
        testing.waitAndAssertTraces(
            trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
      }
    } finally {
      cluster.disconnect();
      environment.shutdown();
    }
  }
}
