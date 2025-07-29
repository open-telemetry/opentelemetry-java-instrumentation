/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1_6;

import com.couchbase.client.core.env.TimeoutConfig;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.env.ClusterEnvironment;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.couchbase.CouchbaseService;

// Couchbase instrumentation is owned upstream, so we don't assert on the contents of the spans,
// only that the instrumentation is properly registered by the agent, meaning some spans were
// generated.
class CouchbaseClient316Test {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("couchbase-container");

  private static CouchbaseContainer couchbase;
  private static Cluster cluster;
  private static Collection collection;

  @BeforeAll
  static void setup() {
    couchbase =
        new CouchbaseContainer("couchbase/server:7.6.0")
            .withExposedPorts(8091)
            .withEnabledServices(CouchbaseService.KV)
            .withBucket(new BucketDefinition("test"))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupAttempts(5)
            .withStartupTimeout(Duration.ofMinutes(2));
    couchbase.start();

    ClusterEnvironment environment =
        ClusterEnvironment.builder()
            .timeoutConfig(TimeoutConfig.kvTimeout(Duration.ofSeconds(30)))
            .build();

    cluster =
        Cluster.connect(
            couchbase.getConnectionString(),
            ClusterOptions.clusterOptions(couchbase.getUsername(), couchbase.getPassword())
                .environment(environment));

    Bucket bucket = cluster.bucket("test");
    collection = bucket.defaultCollection();

    // Wait 1 minute due to slow startup contributing to flakiness
    bucket.waitUntilReady(Duration.ofMinutes(1));
  }

  @AfterAll
  static void cleanup() {
    cluster.disconnect();
    couchbase.stop();
  }

  @Test
  void testEmitsSpans() {
    try {
      collection.get("id");
    } catch (DocumentNotFoundException e) {
      // Expected
    }

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("get"), span -> span.hasName("dispatch_to_server")));
  }
}
